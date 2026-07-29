package jp.hakamap.project.application.editing;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import jp.hakamap.project.application.history.CommandId;
import jp.hakamap.project.application.history.CommandType;
import jp.hakamap.project.application.history.EntityDelta;
import jp.hakamap.project.application.history.ProjectChangeSet;
import jp.hakamap.project.application.history.ValueDelta;
import jp.hakamap.project.domain.model.ProjectAggregate;
import jp.hakamap.project.domain.value.AssetId;

final class ProjectDeltaFactory {
  ProjectChangeSet between(
      ProjectAggregate before,
      ProjectAggregate after,
      CommandType type,
      Instant timestamp,
      UUID commandId) {
    return new ProjectChangeSet(
        new CommandId(commandId),
        type,
        timestamp,
        deltas(before.areas(), after.areas()),
        deltas(before.graves(), after.graves()),
        deltas(before.people(), after.people()),
        deltas(before.assets(), after.assets()),
        before.metadata().name().equals(after.metadata().name())
            ? Optional.empty()
            : Optional.of(ValueDelta.changed(before.metadata().name(), after.metadata().name())),
        before.background().equals(after.background())
            ? Optional.empty()
            : Optional.of(new ValueDelta<>(before.background(), after.background())),
        retainedAssets(before, after));
  }

  private Set<AssetId> retainedAssets(ProjectAggregate before, ProjectAggregate after) {
    java.util.HashSet<AssetId> retained = new java.util.HashSet<>(before.assets().keySet());
    retained.addAll(after.assets().keySet());
    return Set.copyOf(retained);
  }

  private <I, T> List<EntityDelta<I, T>> deltas(Map<I, T> before, Map<I, T> after) {
    List<EntityDelta<I, T>> result = new ArrayList<>();
    java.util.LinkedHashSet<I> ids = new java.util.LinkedHashSet<>(before.keySet());
    ids.addAll(after.keySet());
    for (I id : ids) {
      T oldValue = before.get(id);
      T newValue = after.get(id);
      if (java.util.Objects.equals(oldValue, newValue)) {
        continue;
      }
      if (oldValue == null) {
        result.add(EntityDelta.created(id, newValue));
      } else if (newValue == null) {
        result.add(EntityDelta.deleted(id, oldValue));
      } else {
        result.add(EntityDelta.updated(id, oldValue, newValue));
      }
    }
    return List.copyOf(result);
  }
}
