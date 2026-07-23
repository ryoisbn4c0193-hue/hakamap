package jp.hakamap.project.domain.service;

import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;
import jp.hakamap.project.domain.model.Area;
import jp.hakamap.project.domain.model.Grave;

public final class AreaMembershipResolver {
  public Optional<Area> resolve(Grave grave, Collection<Area> areas) {
    return areas.stream()
        .filter(area -> area.rectangle().containsClosed(grave.rectangle().center()))
        .min(Comparator.comparing(Area::displayOrder).thenComparing(area -> area.id().value()));
  }
}
