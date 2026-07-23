package jp.hakamap.project.infrastructure.recovery;

import java.util.Optional;
import jp.hakamap.project.application.history.ProjectEditingSession;

public record RecoveryApplyResult(
    RecoveryApplyStatus status, String code, Optional<ProjectEditingSession> session) {
  public RecoveryApplyResult {
    session = session == null ? Optional.empty() : session;
  }
}
