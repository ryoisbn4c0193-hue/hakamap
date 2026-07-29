package jp.hakamap.project.application.catalog;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jp.hakamap.infrastructure.fileselection.FileSelectionPurpose;
import jp.hakamap.infrastructure.fileselection.FileSelectionService;
import jp.hakamap.persistence.json.model.catalog.ActiveCatalogProjectV1;
import jp.hakamap.persistence.json.model.catalog.CatalogFileV1;
import jp.hakamap.persistence.json.model.catalog.CatalogProjectV1;
import jp.hakamap.persistence.json.model.catalog.TrashedCatalogProjectV1;
import jp.hakamap.persistence.json.repository.CatalogRepository;
import jp.hakamap.persistence.json.repository.ProjectRepository;
import jp.hakamap.project.application.editing.ProjectAssetStaging;
import jp.hakamap.project.application.recovery.ProjectRecoveryCoordinator;
import jp.hakamap.project.domain.model.ProjectAggregate;
import jp.hakamap.project.domain.model.ProjectMetadata;
import jp.hakamap.project.domain.service.UuidSource;
import jp.hakamap.project.domain.value.ProjectId;
import jp.hakamap.project.domain.value.ProjectName;
import jp.hakamap.project.infrastructure.storage.CommitStatus;
import jp.hakamap.project.infrastructure.storage.ProjectFileLock;
import jp.hakamap.project.infrastructure.storage.ProjectStorageTransactionCoordinator;

public final class ProjectCatalogService {
  private static final String ACTIVE = "active";

  private static final String TRASHED = "trashed";

  private final CatalogPaths paths;

  private final CatalogRepository catalogs;

  private final CatalogWriter catalogWriter;

  private final ProjectRepository projects;

  private final FileSelectionService selections;

  private final OpenProjectManager openProjects;

  private final ProjectStorageTransactionCoordinator storage;

  private final ProjectAssetStaging assetStaging;

  private final ProjectRecoveryCoordinator recovery;

  private final Clock clock;

  private final UuidSource uuids;

  public ProjectCatalogService(
      CatalogPaths paths,
      CatalogRepository catalogs,
      CatalogWriter catalogWriter,
      ProjectRepository projects,
      FileSelectionService selections,
      OpenProjectManager openProjects,
      ProjectStorageTransactionCoordinator storage,
      ProjectAssetStaging assetStaging,
      ProjectRecoveryCoordinator recovery,
      Clock clock,
      UuidSource uuids) {
    this.paths = paths;
    this.catalogs = catalogs;
    this.catalogWriter = catalogWriter;
    this.projects = projects;
    this.selections = selections;
    this.openProjects = openProjects;
    this.storage = storage;
    this.assetStaging = assetStaging;
    this.recovery = recovery;
    this.clock = clock;
    this.uuids = uuids;
  }

  public synchronized CatalogView list() {
    CatalogFileV1 catalog = readCatalog();
    List<ProjectView> projectViews =
        catalog.projects().stream()
            .map(project -> toView(project, catalog.defaultProjectId()))
            .sorted(
                Comparator.comparing(ProjectView::state)
                    .thenComparing(ProjectView::name, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(ProjectView::projectId))
            .toList();
    return new CatalogView(projectViews, openProjects.currentProjectId().orElse(null));
  }

  public synchronized ProjectView create(
      String sessionId, UUID directorySelectionId, String requestedName) {
    ProjectName name = new ProjectName(requestedName);
    Path parent =
        selections.consume(
            directorySelectionId, sessionId, FileSelectionPurpose.PROJECT_CREATE_DIRECTORY);
    UUID projectId = uuids.next();
    Path root = parent.resolve("hakamap-project-" + projectId).toAbsolutePath().normalize();
    if (Files.exists(root)) {
      throw new ProjectCatalogException("project-destination-exists");
    }
    Instant now = clock.instant();
    ProjectAggregate project =
        new ProjectAggregate(
            new ProjectMetadata(new ProjectId(projectId), name, now, now),
            Optional.empty(),
            List.of(),
            List.of(),
            List.of(),
            List.of());
    try {
      Files.createDirectories(root.resolve("assets/backgrounds"));
      Files.createDirectories(root.resolve("assets/attachments"));
      projects.write(root, project);
      CatalogFileV1 catalog = readCatalog();
      requireNoDuplicate(catalog, projectId, root);
      ActiveCatalogProjectV1 entry = active(root, project);
      writeCatalog(withProject(catalog, entry));
      return toView(entry, catalog.defaultProjectId());
    } catch (RuntimeException | IOException exception) {
      deleteNewProjectQuietly(root);
      if (exception instanceof ProjectCatalogException catalogException) {
        throw catalogException;
      }
      throw new ProjectCatalogException("project-create-failed", exception);
    }
  }

  public synchronized ProjectView registerExisting(String sessionId, UUID directorySelectionId) {
    Path root =
        selections.consume(
            directorySelectionId, sessionId, FileSelectionPurpose.PROJECT_RELINK_DIRECTORY);
    ProjectAggregate project = readProject(root);
    CatalogFileV1 catalog = readCatalog();
    UUID projectId = project.metadata().id().value();
    requireNoDuplicate(catalog, projectId, root);
    ActiveCatalogProjectV1 entry = active(root, project);
    writeCatalog(withProject(catalog, entry));
    return toView(entry, catalog.defaultProjectId());
  }

  public synchronized OpenProjectView open(UUID projectId) {
    ActiveCatalogProjectV1 entry = requireActive(readCatalog(), projectId);
    ProjectAggregate aggregate = openProjects.open(projectId, Path.of(entry.path()), projects);
    refreshKnownMetadata(entry, aggregate);
    RecoveryCandidateView candidate =
        recovery
            .inspect(projectId, Path.of(entry.path()), aggregate)
            .map(
                value ->
                    new RecoveryCandidateView(
                        value.recoveryCreatedAt(),
                        value.formalUpdatedAt(),
                        value.stagedAssetCount()))
            .orElse(null);
    return new OpenProjectView(
        projectId,
        aggregate.metadata().name().value(),
        aggregate.metadata().createdAt(),
        aggregate.metadata().updatedAt(),
        candidate);
  }

  public synchronized CloseProjectView close(UUID projectId, String action, String sessionId) {
    if ("cancel".equals(action)) {
      return new CloseProjectView("cancelled");
    }
    if (!"save".equals(action) && !"discard".equals(action)) {
      throw new ProjectCatalogException("project-close-action-invalid");
    }
    if ("save".equals(action)) {
      var result = openProjects.save(projectId, storage, assetStaging.list(projectId));
      if (result.status() == CommitStatus.COMMIT_OUTCOME_UNKNOWN) {
        throw new ProjectCatalogException("storage-commit-outcome-unknown");
      }
      if (result.status() == CommitStatus.NOT_COMMITTED) {
        throw new ProjectCatalogException(saveErrorCode(result.code()));
      }
      if (result.status() == CommitStatus.NO_CHANGES) {
        assetStaging.discard(projectId);
      } else {
        assetStaging.forget(projectId);
      }
      recovery.cleanupAfterSave(projectId);
    } else {
      recovery.cleanupAfterDiscard(projectId);
    }
    openProjects.close(projectId);
    selections.invalidateSession(sessionId);
    return new CloseProjectView("closed");
  }

  private String saveErrorCode(String code) {
    return switch (code) {
      case "project-externally-modified" -> "storage-external-change";
      case "project-lock-lost" -> "storage-project-locked";
      case "storage-space-insufficient" -> "storage-insufficient-space";
      default -> code;
    };
  }

  public synchronized ProjectView relink(
      UUID projectId, String sessionId, UUID directorySelectionId) {
    if (openProjects.isOpen(projectId)) {
      throw new ProjectCatalogException("project-busy");
    }
    Path root =
        selections.consume(
            directorySelectionId, sessionId, FileSelectionPurpose.PROJECT_RELINK_DIRECTORY);
    ProjectAggregate project = readProject(root);
    if (!project.metadata().id().value().equals(projectId)) {
      throw new ProjectCatalogException("project-mismatch");
    }
    CatalogFileV1 catalog = readCatalog();
    requirePathAvailable(catalog, projectId, root);
    ActiveCatalogProjectV1 replacement = active(root, project);
    CatalogFileV1 updated = replace(catalog, replacement, catalog.defaultProjectId());
    writeCatalog(updated);
    return toView(replacement, updated.defaultProjectId());
  }

  public synchronized void unregister(UUID projectId) {
    if (openProjects.isOpen(projectId)) {
      throw new ProjectCatalogException("project-busy");
    }
    CatalogFileV1 catalog = readCatalog();
    requireActive(catalog, projectId);
    UUID defaultId =
        projectId.equals(catalog.defaultProjectId()) ? null : catalog.defaultProjectId();
    writeCatalog(remove(catalog, projectId, defaultId));
  }

  public synchronized ProjectView setDefault(UUID projectId) {
    CatalogFileV1 catalog = readCatalog();
    ActiveCatalogProjectV1 entry = requireActive(catalog, projectId);
    if (!isAvailable(entry)) {
      throw new ProjectCatalogException("catalog-default-invalid");
    }
    CatalogFileV1 updated = new CatalogFileV1(1, projectId, catalog.projects());
    writeCatalog(updated);
    return toView(entry, projectId);
  }

  public synchronized void clearDefault() {
    CatalogFileV1 catalog = readCatalog();
    writeCatalog(new CatalogFileV1(1, null, catalog.projects()));
  }

  public synchronized ProjectView trash(UUID projectId) {
    if (openProjects.isOpen(projectId)) {
      throw new ProjectCatalogException("project-busy");
    }
    CatalogFileV1 catalog = readCatalog();
    ActiveCatalogProjectV1 entry = requireActive(catalog, projectId);
    Path source = Path.of(entry.path());
    Path target = source.getParent().resolve(".hakamap-trash").resolve(projectId.toString());
    if (Files.exists(target)) {
      throw new ProjectCatalogException("project-trash-destination-exists");
    }
    try (ProjectFileLock ignored = ProjectFileLock.acquire(source)) {
      Files.createDirectories(target.getParent());
    } catch (RuntimeException | IOException exception) {
      throw new ProjectCatalogException("project-trash-failed", exception);
    }
    try {
      atomicMove(source, target);
    } catch (IOException exception) {
      throw new ProjectCatalogException("project-trash-failed", exception);
    }
    TrashedCatalogProjectV1 replacement =
        new TrashedCatalogProjectV1(
            projectId,
            target.toAbsolutePath().normalize().toString(),
            source.toAbsolutePath().normalize().toString(),
            entry.lastKnownName(),
            entry.lastKnownCreatedAt(),
            entry.lastKnownUpdatedAt(),
            TRASHED);
    UUID defaultId =
        projectId.equals(catalog.defaultProjectId()) ? null : catalog.defaultProjectId();
    try {
      CatalogFileV1 updated = replace(catalog, replacement, defaultId);
      writeCatalog(updated);
      return toView(replacement, updated.defaultProjectId());
    } catch (RuntimeException exception) {
      try {
        atomicMove(target, source);
      } catch (IOException rollbackFailure) {
        exception.addSuppressed(rollbackFailure);
      }
      throw exception;
    }
  }

  public synchronized ProjectView restore(
      UUID projectId, String sessionId, UUID optionalDirectorySelectionId) {
    CatalogFileV1 catalog = readCatalog();
    TrashedCatalogProjectV1 entry = requireTrashed(catalog, projectId);
    Path source = Path.of(entry.path());
    Path target;
    if (optionalDirectorySelectionId == null) {
      target = Path.of(entry.originalPath());
    } else {
      Path parent =
          selections.consume(
              optionalDirectorySelectionId,
              sessionId,
              FileSelectionPurpose.TRASH_RESTORE_DIRECTORY);
      target = parent.resolve("hakamap-project-" + projectId);
    }
    target = target.toAbsolutePath().normalize();
    if (Files.exists(target)) {
      throw new ProjectCatalogException("project-restore-destination-exists");
    }
    try {
      atomicMove(source, target);
      ProjectAggregate project = readProject(target);
      if (!project.metadata().id().value().equals(projectId)) {
        throw new ProjectCatalogException("project-mismatch");
      }
      ActiveCatalogProjectV1 replacement = active(target, project);
      CatalogFileV1 updated = replace(catalog, replacement, catalog.defaultProjectId());
      writeCatalog(updated);
      return toView(replacement, updated.defaultProjectId());
    } catch (RuntimeException | IOException exception) {
      if (Files.exists(target) && !Files.exists(source)) {
        try {
          atomicMove(target, source);
        } catch (IOException rollbackFailure) {
          exception.addSuppressed(rollbackFailure);
        }
      }
      if (exception instanceof ProjectCatalogException catalogException) {
        throw catalogException;
      }
      throw new ProjectCatalogException("project-restore-failed", exception);
    }
  }

  public synchronized void permanentlyDelete(UUID projectId) {
    CatalogFileV1 catalog = readCatalog();
    TrashedCatalogProjectV1 entry = requireTrashed(catalog, projectId);
    deleteTree(Path.of(entry.path()));
    writeCatalog(remove(catalog, projectId, catalog.defaultProjectId()));
  }

  private CatalogFileV1 readCatalog() {
    if (!Files.isRegularFile(paths.catalogFile())) {
      return new CatalogFileV1(1, null, List.of());
    }
    return catalogs.read(paths.catalogFile());
  }

  private void writeCatalog(CatalogFileV1 catalog) {
    catalogWriter.write(paths.catalogFile(), catalog);
  }

  private ProjectAggregate readProject(Path root) {
    try {
      return projects.read(root);
    } catch (RuntimeException exception) {
      throw new ProjectCatalogException("catalog-project-not-found", exception);
    }
  }

  private ActiveCatalogProjectV1 active(Path root, ProjectAggregate project) {
    ProjectMetadata metadata = project.metadata();
    return new ActiveCatalogProjectV1(
        metadata.id().value(),
        root.toAbsolutePath().normalize().toString(),
        metadata.name().value(),
        metadata.createdAt(),
        metadata.updatedAt(),
        ACTIVE);
  }

  private ProjectView toView(CatalogProjectV1 project, UUID defaultProjectId) {
    Path path = Path.of(project.path());
    return new ProjectView(
        project.projectId(),
        project.lastKnownName(),
        project.lastKnownCreatedAt(),
        project.lastKnownUpdatedAt(),
        project.state(),
        project.projectId().equals(defaultProjectId),
        Files.isDirectory(path) && Files.isRegularFile(path.resolve("project.json")),
        safeLocationLabel(project.path()),
        hasRecoveryCandidate(project, path));
  }

  private boolean hasRecoveryCandidate(CatalogProjectV1 project, Path root) {
    if (!(project instanceof ActiveCatalogProjectV1)
        || !Files.isRegularFile(root.resolve("project.json"))) {
      return false;
    }
    try {
      ProjectAggregate formalProject = projects.read(root);
      return recovery.inspect(project.projectId(), root, formalProject).isPresent();
    } catch (RuntimeException exception) {
      return false;
    }
  }

  private String safeLocationLabel(String storedPath) {
    String normalizedSeparators = storedPath.replace('\\', '/');
    int lastSeparator = normalizedSeparators.lastIndexOf('/');
    return lastSeparator < 0
        ? normalizedSeparators
        : normalizedSeparators.substring(lastSeparator + 1);
  }

  private void refreshKnownMetadata(ActiveCatalogProjectV1 existing, ProjectAggregate aggregate) {
    ActiveCatalogProjectV1 refreshed = active(Path.of(existing.path()), aggregate);
    if (!refreshed.equals(existing)) {
      CatalogFileV1 catalog = readCatalog();
      writeCatalog(replace(catalog, refreshed, catalog.defaultProjectId()));
    }
  }

  private boolean isAvailable(ActiveCatalogProjectV1 project) {
    Path root = Path.of(project.path());
    return Files.isDirectory(root) && Files.isRegularFile(root.resolve("project.json"));
  }

  private void requireNoDuplicate(CatalogFileV1 catalog, UUID projectId, Path root) {
    boolean duplicateId =
        catalog.projects().stream().anyMatch(project -> project.projectId().equals(projectId));
    if (duplicateId) {
      throw new ProjectCatalogException("catalog-project-duplicate");
    }
    requirePathAvailable(catalog, projectId, root);
  }

  private void requirePathAvailable(CatalogFileV1 catalog, UUID projectId, Path root) {
    Path normalized = root.toAbsolutePath().normalize();
    boolean duplicatePath =
        catalog.projects().stream()
            .filter(project -> !project.projectId().equals(projectId))
            .map(project -> Path.of(project.path()).toAbsolutePath().normalize())
            .anyMatch(normalized::equals);
    if (duplicatePath) {
      throw new ProjectCatalogException("catalog-project-duplicate");
    }
  }

  private ActiveCatalogProjectV1 requireActive(CatalogFileV1 catalog, UUID projectId) {
    return catalog.projects().stream()
        .filter(ActiveCatalogProjectV1.class::isInstance)
        .map(ActiveCatalogProjectV1.class::cast)
        .filter(project -> project.projectId().equals(projectId))
        .findFirst()
        .orElseThrow(() -> new ProjectCatalogException("catalog-project-not-found"));
  }

  private TrashedCatalogProjectV1 requireTrashed(CatalogFileV1 catalog, UUID projectId) {
    return catalog.projects().stream()
        .filter(TrashedCatalogProjectV1.class::isInstance)
        .map(TrashedCatalogProjectV1.class::cast)
        .filter(project -> project.projectId().equals(projectId))
        .findFirst()
        .orElseThrow(() -> new ProjectCatalogException("catalog-project-not-found"));
  }

  private CatalogFileV1 withProject(CatalogFileV1 catalog, CatalogProjectV1 project) {
    List<CatalogProjectV1> entries = new ArrayList<>(catalog.projects());
    entries.add(project);
    return new CatalogFileV1(1, catalog.defaultProjectId(), entries);
  }

  private CatalogFileV1 replace(
      CatalogFileV1 catalog, CatalogProjectV1 replacement, UUID defaultProjectId) {
    List<CatalogProjectV1> entries =
        catalog.projects().stream()
            .map(
                project ->
                    project.projectId().equals(replacement.projectId()) ? replacement : project)
            .toList();
    if (entries.stream().noneMatch(project -> project == replacement)) {
      throw new ProjectCatalogException("catalog-project-not-found");
    }
    return new CatalogFileV1(1, defaultProjectId, entries);
  }

  private CatalogFileV1 remove(CatalogFileV1 catalog, UUID projectId, UUID defaultProjectId) {
    List<CatalogProjectV1> entries =
        catalog.projects().stream()
            .filter(project -> !project.projectId().equals(projectId))
            .toList();
    if (entries.size() == catalog.projects().size()) {
      throw new ProjectCatalogException("catalog-project-not-found");
    }
    return new CatalogFileV1(1, defaultProjectId, entries);
  }

  private void atomicMove(Path source, Path target) throws IOException {
    try {
      Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
    } catch (AtomicMoveNotSupportedException exception) {
      throw new ProjectCatalogException("storage-atomic-move-unsupported", exception);
    }
  }

  private void deleteTree(Path root) {
    if (!Files.exists(root)) {
      throw new ProjectCatalogException("catalog-project-not-found");
    }
    try {
      Files.walkFileTree(
          root,
          new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes)
                throws IOException {
              Files.delete(file);
              return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path directory, IOException exception)
                throws IOException {
              if (exception != null) {
                throw exception;
              }
              Files.delete(directory);
              return FileVisitResult.CONTINUE;
            }
          });
    } catch (IOException exception) {
      throw new ProjectCatalogException("project-delete-failed", exception);
    }
  }

  private void deleteNewProjectQuietly(Path root) {
    if (!Files.exists(root)) {
      return;
    }
    try {
      Files.walk(root)
          .sorted(Comparator.reverseOrder())
          .forEach(
              path -> {
                try {
                  Files.deleteIfExists(path);
                } catch (IOException ignored) {
                  // 作成失敗後の清掃は元の失敗を優先する。
                }
              });
    } catch (IOException ignored) {
      // 作成失敗後の清掃は元の失敗を優先する。
    }
  }

  public record CatalogView(List<ProjectView> projects, UUID openProjectId) {
    public CatalogView {
      projects = List.copyOf(projects);
    }
  }

  public record ProjectView(
      UUID projectId,
      String name,
      Instant createdAt,
      Instant updatedAt,
      String state,
      boolean defaultProject,
      boolean available,
      String locationLabel,
      boolean recoveryCandidate) {}

  public record OpenProjectView(
      UUID projectId,
      String name,
      Instant createdAt,
      Instant updatedAt,
      RecoveryCandidateView recoveryCandidate) {}

  public record RecoveryCandidateView(
      Instant recoveryCreatedAt, Instant formalUpdatedAt, int stagedAssetCount) {}

  public record CloseProjectView(String status) {}
}
