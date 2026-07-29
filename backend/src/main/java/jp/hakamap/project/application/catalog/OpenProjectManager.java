package jp.hakamap.project.application.catalog;

import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jp.hakamap.persistence.json.repository.ProjectRepository;
import jp.hakamap.project.application.history.ProjectEditingSession;
import jp.hakamap.project.application.history.ProjectFingerprintCalculator;
import jp.hakamap.project.domain.model.ProjectAggregate;
import jp.hakamap.project.infrastructure.storage.ProjectFileLock;
import jp.hakamap.project.infrastructure.storage.ProjectStorageTransactionCoordinator;
import jp.hakamap.project.infrastructure.storage.SaveResult;
import jp.hakamap.project.infrastructure.storage.StagedAsset;

public final class OpenProjectManager implements AutoCloseable {
  private OpenProject openProject;

  public synchronized ProjectAggregate open(
      UUID expectedProjectId, Path root, ProjectRepository projects) {
    if (openProject != null) {
      if (openProject.projectId().equals(expectedProjectId)) {
        return openProject.aggregate();
      }
      throw new ProjectCatalogException("project-busy");
    }
    ProjectFileLock lock = ProjectFileLock.acquire(root);
    try {
      ProjectAggregate aggregate = projects.read(root);
      if (!aggregate.metadata().id().value().equals(expectedProjectId)) {
        throw new ProjectCatalogException("project-mismatch");
      }
      openProject = new OpenProject(expectedProjectId, root, aggregate, lock);
      return aggregate;
    } catch (RuntimeException exception) {
      lock.close();
      throw exception;
    }
  }

  public synchronized boolean isOpen(UUID projectId) {
    return openProject != null && openProject.projectId().equals(projectId);
  }

  public synchronized Optional<UUID> currentProjectId() {
    return openProject == null ? Optional.empty() : Optional.of(openProject.projectId());
  }

  public synchronized ProjectEditingSession editingSession(
      UUID projectId, ProjectFingerprintCalculator fingerprints) {
    OpenProject project = requireOpen(projectId);
    if (project.editingSession() == null) {
      project.editingSession =
          new ProjectEditingSession(project.aggregate(), sha256(project.root()), fingerprints);
    }
    return project.editingSession();
  }

  public synchronized Path projectRoot(UUID projectId) {
    return requireOpen(projectId).root();
  }

  public synchronized ProjectAggregate current(UUID projectId) {
    return requireOpen(projectId).aggregate();
  }

  public synchronized SaveResult save(
      UUID projectId,
      ProjectStorageTransactionCoordinator coordinator,
      List<StagedAsset> stagedAssets) {
    OpenProject project = requireOpen(projectId);
    if (project.editingSession() == null) {
      return SaveResult.noChanges();
    }
    return coordinator.save(
        project.root(),
        project.editingSession(),
        project.editingSession().revision(),
        project.lock(),
        stagedAssets,
        null);
  }

  public synchronized void close(UUID projectId) {
    if (!isOpen(projectId)) {
      throw new ProjectCatalogException("project-not-open");
    }
    openProject.lock().close();
    openProject = null;
  }

  private OpenProject requireOpen(UUID projectId) {
    if (!isOpen(projectId)) {
      throw new ProjectCatalogException(
          openProject == null ? "project-not-open" : "project-mismatch");
    }
    return openProject;
  }

  private String sha256(Path root) {
    try {
      byte[] bytes = java.nio.file.Files.readAllBytes(root.resolve("project.json"));
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (java.io.IOException | NoSuchAlgorithmException exception) {
      throw new ProjectCatalogException("catalog-project-not-found", exception);
    }
  }

  @Override
  public synchronized void close() {
    if (openProject != null) {
      openProject.lock().close();
      openProject = null;
    }
  }

  private static final class OpenProject {
    private final UUID projectId;

    private final Path root;

    private final ProjectAggregate aggregate;

    private final ProjectFileLock lock;

    private ProjectEditingSession editingSession;

    private OpenProject(
        UUID projectId, Path root, ProjectAggregate aggregate, ProjectFileLock lock) {
      this.projectId = projectId;
      this.root = root;
      this.aggregate = aggregate;
      this.lock = lock;
    }

    private UUID projectId() {
      return projectId;
    }

    private Path root() {
      return root;
    }

    private ProjectAggregate aggregate() {
      return editingSession == null ? aggregate : editingSession.current();
    }

    private ProjectFileLock lock() {
      return lock;
    }

    private ProjectEditingSession editingSession() {
      return editingSession;
    }
  }
}
