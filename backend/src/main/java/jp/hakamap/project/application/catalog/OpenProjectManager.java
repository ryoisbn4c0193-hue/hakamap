package jp.hakamap.project.application.catalog;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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

  public synchronized Optional<OpenEditingSession> currentEditingSession() {
    if (openProject == null || openProject.editingSession() == null) {
      return Optional.empty();
    }
    return Optional.of(
        new OpenEditingSession(
            openProject.projectId(), openProject.root(), openProject.editingSession()));
  }

  public synchronized void replaceEditingSession(
      UUID projectId, ProjectEditingSession editingSession) {
    requireOpen(projectId).editingSession = editingSession;
  }

  public synchronized ProjectAggregate reload(
      UUID projectId, ProjectRepository projects, ProjectFingerprintCalculator fingerprints) {
    OpenProject open = requireOpen(projectId);
    ProjectAggregate aggregate = projects.read(open.root());
    open.aggregate = aggregate;
    open.editingSession = new ProjectEditingSession(aggregate, sha256(open.root()), fingerprints);
    return aggregate;
  }

  public synchronized ProjectAggregate replaceProjectDirectory(
      UUID projectId,
      Path replacement,
      ProjectRepository projects,
      ProjectFingerprintCalculator fingerprints) {
    OpenProject open = requireOpen(projectId);
    Path root = open.root();
    Path previous = root.resolveSibling(".hakamap-restore-previous-" + UUID.randomUUID() + ".tmp");
    Path rejected = root.resolveSibling(".hakamap-restore-rejected-" + UUID.randomUUID() + ".tmp");
    open.lock().close();
    boolean oldMoved = false;
    boolean replacementMoved = false;
    try {
      atomicMove(root, previous);
      oldMoved = true;
      atomicMove(replacement, root);
      replacementMoved = true;
      ProjectFileLock newLock = ProjectFileLock.acquire(root);
      ProjectAggregate aggregate;
      try {
        aggregate = projects.read(root);
        if (!aggregate.metadata().id().value().equals(projectId)) {
          throw new ProjectCatalogException("project-mismatch");
        }
      } catch (RuntimeException exception) {
        newLock.close();
        throw exception;
      }
      open.lock = newLock;
      open.aggregate = aggregate;
      open.editingSession = new ProjectEditingSession(aggregate, sha256(root), fingerprints);
      deleteTreeQuietly(previous);
      return aggregate;
    } catch (IOException | RuntimeException exception) {
      if (replacementMoved) {
        try {
          atomicMove(root, rejected);
        } catch (IOException | RuntimeException quarantineFailure) {
          exception.addSuppressed(quarantineFailure);
        }
      }
      if (oldMoved) {
        try {
          atomicMove(previous, root);
        } catch (IOException rollbackFailure) {
          exception.addSuppressed(rollbackFailure);
        }
      }
      try {
        open.lock = ProjectFileLock.acquire(root);
      } catch (RuntimeException lockFailure) {
        exception.addSuppressed(lockFailure);
      }
      deleteTreeQuietly(rejected);
      throw new ProjectCatalogException("backup-restore-failed", exception);
    }
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

  private void atomicMove(Path source, Path target) throws IOException {
    try {
      Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
    } catch (AtomicMoveNotSupportedException exception) {
      throw new ProjectCatalogException("storage-atomic-move-unsupported", exception);
    }
  }

  private void deleteTreeQuietly(Path root) {
    if (!Files.exists(root)) {
      return;
    }
    try (var stream = Files.walk(root)) {
      stream
          .sorted(java.util.Comparator.reverseOrder())
          .forEach(
              path -> {
                try {
                  Files.deleteIfExists(path);
                } catch (IOException ignored) {
                  // 確定後の旧ディレクトリは次回清掃対象として残す。
                }
              });
    } catch (IOException ignored) {
      // 確定後の旧ディレクトリは次回清掃対象として残す。
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

    private ProjectAggregate aggregate;

    private ProjectFileLock lock;

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

  public record OpenEditingSession(
      UUID projectId, Path projectRoot, ProjectEditingSession editingSession) {}
}
