package jp.hakamap.project.infrastructure.storage;

import java.util.List;

public record SaveResult(CommitStatus status, String code, List<String> warnings) {
  public SaveResult {
    warnings = List.copyOf(warnings);
  }

  public static SaveResult noChanges() {
    return new SaveResult(CommitStatus.NO_CHANGES, "no-changes", List.of());
  }

  public static SaveResult committed(List<String> warnings) {
    return new SaveResult(CommitStatus.COMMITTED, "saved", warnings);
  }

  public static SaveResult notCommitted(String code) {
    return new SaveResult(CommitStatus.NOT_COMMITTED, code, List.of());
  }

  public static SaveResult outcomeUnknown() {
    return new SaveResult(CommitStatus.COMMIT_OUTCOME_UNKNOWN, "commit-outcome-unknown", List.of());
  }
}
