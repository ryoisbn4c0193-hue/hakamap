package jp.hakamap.project.infrastructure.recovery;

import java.util.List;
import java.util.Optional;
import jp.hakamap.persistence.json.model.recovery.StagedAssetV1;
import jp.hakamap.project.application.history.ProjectEditingSession;

public record RecoveryApplyResult(
    RecoveryApplyStatus status,
    String code,
    Optional<ProjectEditingSession> session,
    List<StagedAssetV1> stagedAssets) {
  public RecoveryApplyResult {
    session = session == null ? Optional.empty() : session;
    stagedAssets = stagedAssets == null ? List.of() : List.copyOf(stagedAssets);
  }
}
