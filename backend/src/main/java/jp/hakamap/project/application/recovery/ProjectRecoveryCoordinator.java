package jp.hakamap.project.application.recovery;

import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import jp.hakamap.project.application.catalog.OpenProjectManager;
import jp.hakamap.project.application.editing.ProjectAssetStaging;
import jp.hakamap.project.domain.model.ProjectAggregate;
import jp.hakamap.project.infrastructure.recovery.RecoveryApplyResult;
import jp.hakamap.project.infrastructure.recovery.RecoveryApplyStatus;
import jp.hakamap.project.infrastructure.recovery.RecoverySnapshotService;

public final class ProjectRecoveryCoordinator {
  private final OpenProjectManager openProjects;

  private final ProjectAssetStaging assetStaging;

  private final RecoverySnapshotService snapshots;

  public ProjectRecoveryCoordinator(
      OpenProjectManager openProjects,
      ProjectAssetStaging assetStaging,
      RecoverySnapshotService snapshots) {
    this.openProjects = openProjects;
    this.assetStaging = assetStaging;
    this.snapshots = snapshots;
  }

  public synchronized void writeOpenProjectIfDue() {
    openProjects
        .currentEditingSession()
        .ifPresent(
            open ->
                snapshots.writeIfDue(
                    open.editingSession(), assetStaging.recoveryEntries(open.projectId())));
  }

  public synchronized Optional<RecoverySnapshotService.RecoveryCandidate> inspect(
      UUID projectId, Path projectRoot, ProjectAggregate formalProject) {
    return snapshots.inspect(
        snapshots.recoveryFile(projectId), projectRoot.resolve("project.json"), formalProject);
  }

  public synchronized RecoveryResult apply(UUID projectId) {
    Path projectRoot = openProjects.projectRoot(projectId);
    ProjectAggregate formalProject = openProjects.current(projectId);
    RecoveryApplyResult result =
        snapshots.apply(
            snapshots.recoveryFile(projectId), projectRoot.resolve("project.json"), formalProject);
    if (result.status() != RecoveryApplyStatus.APPLIED) {
      return new RecoveryResult(result.status().name().toLowerCase(), result.code());
    }
    var recoveredSession = result.session().orElseThrow();
    assetStaging.restore(projectId, recoveredSession.current(), result.stagedAssets());
    openProjects.replaceEditingSession(projectId, recoveredSession);
    return new RecoveryResult("applied", result.code());
  }

  public synchronized RecoveryResult discard(UUID projectId) {
    Path projectRoot = openProjects.projectRoot(projectId);
    ProjectAggregate formalProject = openProjects.current(projectId);
    RecoveryApplyResult result =
        snapshots.apply(
            snapshots.recoveryFile(projectId), projectRoot.resolve("project.json"), formalProject);
    if (result.status() != RecoveryApplyStatus.APPLIED) {
      return new RecoveryResult(result.status().name().toLowerCase(), result.code());
    }
    var recoveredSession = result.session().orElseThrow();
    assetStaging.restore(projectId, recoveredSession.current(), result.stagedAssets());
    assetStaging.discardStrict(projectId);
    snapshots.delete(projectId);
    return new RecoveryResult("discarded", "recovery-discarded");
  }

  public synchronized void cleanupAfterSave(UUID projectId) {
    snapshots.delete(projectId);
  }

  public synchronized void cleanupAfterDiscard(UUID projectId) {
    assetStaging.discardStrict(projectId);
    snapshots.delete(projectId);
  }

  public record RecoveryResult(String status, String code) {}
}
