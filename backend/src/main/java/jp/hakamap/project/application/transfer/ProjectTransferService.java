package jp.hakamap.project.application.transfer;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import jp.hakamap.infrastructure.fileselection.FileSelectionPurpose;
import jp.hakamap.infrastructure.fileselection.FileSelectionService;
import jp.hakamap.persistence.json.repository.ProjectRepository;
import jp.hakamap.project.application.catalog.OpenProjectManager;
import jp.hakamap.project.application.catalog.ProjectCatalogService;
import jp.hakamap.project.application.history.ProjectFingerprintCalculator;

public final class ProjectTransferService {
  private static final Duration TOKEN_LIFETIME = Duration.ofMinutes(5);

  private final ProjectArchiveService archives;

  private final OpenProjectManager openProjects;

  private final ProjectRepository projects;

  private final ProjectFingerprintCalculator fingerprints;

  private final FileSelectionService selections;

  private final ProjectCatalogService catalog;

  private final Clock clock;

  private final Map<String, BackupCandidate> candidates = new LinkedHashMap<>();

  public ProjectTransferService(
      ProjectArchiveService archives,
      OpenProjectManager openProjects,
      ProjectRepository projects,
      ProjectFingerprintCalculator fingerprints,
      FileSelectionService selections,
      ProjectCatalogService catalog,
      Clock clock) {
    this.archives = archives;
    this.openProjects = openProjects;
    this.projects = projects;
    this.fingerprints = fingerprints;
    this.selections = selections;
    this.catalog = catalog;
    this.clock = clock;
  }

  public synchronized BackupListResponse backups(UUID projectId, long revision, String sessionId) {
    discardExpired();
    Path root = openProjects.projectRoot(projectId);
    List<BackupListItemResponse> items = new ArrayList<>();
    scan(root.resolve("backup/automatic"), "automatic", projectId, sessionId, items);
    scan(root.resolve("backup/pre-restore"), "preRestore", projectId, sessionId, items);
    items.sort(Comparator.comparing(BackupListItemResponse::createdAt).reversed());
    return new BackupListResponse(projectId, revision, List.copyOf(items));
  }

  public synchronized void automaticBackup(UUID projectId) {
    archives.createAutomaticBackup(openProjects.projectRoot(projectId));
  }

  public synchronized void export(UUID projectId, UUID selectionId, String sessionId) {
    export(projectId, selectionId, sessionId, OperationControl.NONE);
  }

  public synchronized void export(
      UUID projectId, UUID selectionId, String sessionId, OperationControl control) {
    Path destination =
        selections.consume(selectionId, sessionId, FileSelectionPurpose.EXPORT_DESTINATION);
    archives.exportArchive(openProjects.projectRoot(projectId), destination, control);
  }

  public synchronized UUID importArchive(
      UUID archiveSelectionId, UUID destinationSelectionId, String sessionId) {
    return importArchive(
        archiveSelectionId, destinationSelectionId, sessionId, OperationControl.NONE);
  }

  public synchronized UUID importArchive(
      UUID archiveSelectionId,
      UUID destinationSelectionId,
      String sessionId,
      OperationControl control) {
    if (openProjects.currentProjectId().isPresent()) {
      throw new ProjectTransferException("import-project-open");
    }
    Path archive =
        selections.consume(archiveSelectionId, sessionId, FileSelectionPurpose.IMPORT_ARCHIVE);
    Path parent =
        selections.consume(
            destinationSelectionId, sessionId, FileSelectionPurpose.IMPORT_DESTINATION_DIRECTORY);
    Path temporary =
        parent
            .resolve(".hakamap-import-" + UUID.randomUUID() + ".tmp")
            .toAbsolutePath()
            .normalize();
    ProjectArchiveService.ExtractedProject extracted =
        archives.extractAndValidate(archive, temporary, control);
    UUID projectId = extracted.project().metadata().id().value();
    Path target = parent.resolve("hakamap-project-" + projectId).toAbsolutePath().normalize();
    if (Files.exists(target)) {
      ProjectArchiveService.deleteTreeQuietly(temporary);
      throw new ProjectTransferException("project-destination-exists");
    }
    try {
      control.beginCommit();
      Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
      catalog.registerImported(target);
      return projectId;
    } catch (AtomicMoveNotSupportedException exception) {
      ProjectArchiveService.deleteTreeQuietly(temporary);
      throw new ProjectTransferException("archive-atomic-move-unsupported", exception);
    } catch (IOException | RuntimeException exception) {
      ProjectArchiveService.deleteTreeQuietly(temporary);
      if (exception instanceof ProjectTransferException transfer) {
        throw transfer;
      }
      throw new ProjectTransferException("import-failed", exception);
    }
  }

  public synchronized void restore(
      UUID projectId,
      long expectedRevision,
      boolean confirmedNoUnsavedChanges,
      String backupId,
      String backupVersion,
      String sessionId) {
    restore(
        projectId,
        expectedRevision,
        confirmedNoUnsavedChanges,
        backupId,
        backupVersion,
        sessionId,
        OperationControl.NONE);
  }

  public synchronized void restore(
      UUID projectId,
      long expectedRevision,
      boolean confirmedNoUnsavedChanges,
      String backupId,
      String backupVersion,
      String sessionId,
      OperationControl control) {
    if (!confirmedNoUnsavedChanges) {
      throw new ProjectTransferException("backup-project-dirty");
    }
    var editing =
        openProjects
            .currentEditingSession()
            .orElseThrow(() -> new ProjectTransferException("project-not-open"));
    if (!editing.projectId().equals(projectId)
        || editing.editingSession().revision() != expectedRevision
        || editing.editingSession().dirty()) {
      throw new ProjectTransferException("backup-project-dirty");
    }
    discardExpired();
    BackupCandidate candidate = candidates.remove(backupId);
    if (candidate == null
        || !candidate.sessionId().equals(sessionId)
        || !candidate.projectId().equals(projectId)
        || !candidate.version().equals(backupVersion)) {
      throw new ProjectTransferException("backup-not-found");
    }
    ProjectArchiveService.ArchiveInspection current = archives.inspect(candidate.path());
    if (!current.archiveSha256().equals(candidate.archiveSha256())
        || current.sizeBytes() != candidate.sizeBytes()
        || !current.lastModified().equals(candidate.lastModified())) {
      throw new ProjectTransferException("backup-version-conflict");
    }
    Path root = openProjects.projectRoot(projectId);
    archives.createPreRestoreBackup(root, control);
    Path temporary = root.resolveSibling(".hakamap-restore-" + UUID.randomUUID() + ".tmp");
    long backupBytes = directorySize(root.resolve("backup"), control);
    ProjectArchiveService.ExtractedProject extracted =
        archives.extractAndValidate(candidate.path(), temporary, control, backupBytes);
    try {
      copyTree(root.resolve("backup"), extracted.directory().resolve("backup"), control);
      control.beginCommit();
      openProjects.replaceProjectDirectory(
          projectId, extracted.directory(), projects, fingerprints);
    } catch (IOException | RuntimeException exception) {
      if (exception instanceof ProjectTransferException transfer) {
        throw transfer;
      }
      throw new ProjectTransferException("backup-restore-failed", exception);
    } finally {
      ProjectArchiveService.deleteTreeQuietly(temporary);
    }
  }

  private void scan(
      Path directory,
      String type,
      UUID projectId,
      String sessionId,
      List<BackupListItemResponse> output) {
    if (!Files.isDirectory(directory)) {
      return;
    }
    try (var stream = Files.list(directory)) {
      for (Path path : stream.filter(value -> value.toString().endsWith(".zip")).toList()) {
        try {
          var inspection = archives.inspect(path);
          if (!inspection.projectId().equals(projectId)) {
            continue;
          }
          String id = UUID.randomUUID().toString();
          String version = UUID.randomUUID().toString();
          candidates.put(
              id,
              new BackupCandidate(
                  sessionId,
                  projectId,
                  path,
                  version,
                  inspection.archiveSha256(),
                  inspection.sizeBytes(),
                  inspection.lastModified(),
                  clock.instant().plus(TOKEN_LIFETIME)));
          output.add(
              new BackupListItemResponse(
                  id,
                  type,
                  inspection.createdAt(),
                  inspection.sizeBytes(),
                  inspection.applicationVersion(),
                  inspection.projectName(),
                  true,
                  null,
                  version));
        } catch (RuntimeException ignored) {
          output.add(
              new BackupListItemResponse(
                  UUID.randomUUID().toString(),
                  type,
                  Files.getLastModifiedTime(path).toInstant(),
                  Files.size(path),
                  null,
                  null,
                  false,
                  "corrupted",
                  UUID.randomUUID().toString()));
        }
      }
    } catch (IOException exception) {
      throw new ProjectTransferException("archive-list-failed", exception);
    }
  }

  private void copyTree(Path source, Path target, OperationControl control) throws IOException {
    if (!Files.exists(source)) {
      return;
    }
    List<Path> files;
    try (var stream = Files.walk(source)) {
      files = stream.filter(Files::isRegularFile).toList();
    }
    for (Path file : files) {
      control.checkpoint();
      Path destination = target.resolve(source.relativize(file)).normalize();
      if (!destination.startsWith(target.toAbsolutePath().normalize())) {
        throw new ProjectTransferException("archive-path-invalid");
      }
      Files.createDirectories(destination.getParent());
      try (var input = Files.newInputStream(file);
          var output = Files.newOutputStream(destination)) {
        byte[] buffer = new byte[64 * 1024];
        while (true) {
          control.checkpoint();
          int read = input.read(buffer);
          if (read < 0) {
            break;
          }
          output.write(buffer, 0, read);
        }
      }
    }
  }

  private long directorySize(Path directory, OperationControl control) throws IOException {
    if (!Files.exists(directory)) {
      return 0;
    }
    long total = 0;
    try (var stream = Files.walk(directory)) {
      for (Path file : stream.filter(Files::isRegularFile).toList()) {
        control.checkpoint();
        long size = Files.size(file);
        if (size > Long.MAX_VALUE - total) {
          throw new ProjectTransferException("archive-space-insufficient");
        }
        total += size;
      }
    }
    return total;
  }

  private void discardExpired() {
    Instant now = clock.instant();
    candidates.values().removeIf(candidate -> !now.isBefore(candidate.expiresAt()));
  }

  public record BackupListResponse(
      UUID projectId, long revision, List<BackupListItemResponse> items) {}

  public record BackupListItemResponse(
      String backupId,
      String backupType,
      Instant createdAt,
      long sizeBytes,
      String applicationVersion,
      String projectName,
      boolean restorable,
      String unavailableReason,
      String backupVersion) {}

  private record BackupCandidate(
      String sessionId,
      UUID projectId,
      Path path,
      String version,
      String archiveSha256,
      long sizeBytes,
      java.nio.file.attribute.FileTime lastModified,
      Instant expiresAt) {}
}
