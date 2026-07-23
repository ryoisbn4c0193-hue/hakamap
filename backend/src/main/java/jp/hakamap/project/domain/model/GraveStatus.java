package jp.hakamap.project.domain.model;

import java.util.Objects;
import java.util.Set;
import jp.hakamap.project.domain.value.AreaId;
import jp.hakamap.project.domain.value.DomainWarningCode;
import jp.hakamap.project.domain.value.GraveCompletionStatus;
import jp.hakamap.project.domain.value.IncompleteReason;

public record GraveStatus(
    java.util.Optional<AreaId> areaId,
    GraveCompletionStatus completionStatus,
    Set<IncompleteReason> incompleteReasons,
    Set<DomainWarningCode> warnings) {
  public GraveStatus {
    areaId = Objects.requireNonNull(areaId, "areaId");
    Objects.requireNonNull(completionStatus, "completionStatus");
    incompleteReasons = Set.copyOf(incompleteReasons);
    warnings = Set.copyOf(warnings);
  }
}
