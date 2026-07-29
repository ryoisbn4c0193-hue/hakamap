package jp.hakamap.project.application.catalog;

import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import jp.hakamap.persistence.json.repository.ProjectRepository;
import jp.hakamap.project.domain.model.ProjectAggregate;
import jp.hakamap.project.infrastructure.storage.ProjectFileLock;

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

  public synchronized void close(UUID projectId) {
    if (!isOpen(projectId)) {
      throw new ProjectCatalogException("project-not-open");
    }
    openProject.lock().close();
    openProject = null;
  }

  @Override
  public synchronized void close() {
    if (openProject != null) {
      openProject.lock().close();
      openProject = null;
    }
  }

  private record OpenProject(
      UUID projectId, Path root, ProjectAggregate aggregate, ProjectFileLock lock) {}
}
