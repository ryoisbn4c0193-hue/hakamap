package jp.hakamap.project.domain.service;

import java.util.EnumSet;
import java.util.Optional;
import jp.hakamap.project.domain.model.Area;
import jp.hakamap.project.domain.model.Grave;
import jp.hakamap.project.domain.model.GraveStatus;
import jp.hakamap.project.domain.value.DomainWarningCode;
import jp.hakamap.project.domain.value.GraveCompletionStatus;
import jp.hakamap.project.domain.value.IncompleteReason;

public final class GraveStatusDeriver {
  private final AreaMembershipResolver membershipResolver;

  public GraveStatusDeriver(AreaMembershipResolver membershipResolver) {
    this.membershipResolver = membershipResolver;
  }

  public GraveStatus derive(Grave grave, java.util.Collection<Area> areas) {
    Optional<Area> area = membershipResolver.resolve(grave, areas);
    EnumSet<IncompleteReason> incomplete = EnumSet.noneOf(IncompleteReason.class);
    EnumSet<DomainWarningCode> warnings = EnumSet.noneOf(DomainWarningCode.class);
    if (area.isEmpty()) {
      incomplete.add(IncompleteReason.UNASSIGNED);
      warnings.add(DomainWarningCode.UNASSIGNED);
    }
    if (grave.managementNumber().isEmpty()) {
      incomplete.add(IncompleteReason.UNNUMBERED);
      warnings.add(DomainWarningCode.UNNUMBERED);
    }
    if (area.isPresent() && !area.orElseThrow().rectangle().containsClosed(grave.rectangle())) {
      warnings.add(DomainWarningCode.OUTSIDE_AREA_BOUNDS);
    }
    GraveCompletionStatus completion =
        incomplete.isEmpty() ? GraveCompletionStatus.COMPLETE : GraveCompletionStatus.INCOMPLETE;
    return new GraveStatus(area.map(Area::id), completion, incomplete, warnings);
  }
}
