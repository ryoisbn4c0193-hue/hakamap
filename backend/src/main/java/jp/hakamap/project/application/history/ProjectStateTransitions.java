package jp.hakamap.project.application.history;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import jp.hakamap.project.domain.model.Area;
import jp.hakamap.project.domain.model.AssetMetadata;
import jp.hakamap.project.domain.model.BackgroundPlacement;
import jp.hakamap.project.domain.model.Grave;
import jp.hakamap.project.domain.model.Person;
import jp.hakamap.project.domain.model.ProjectAggregate;
import jp.hakamap.project.domain.model.ProjectMetadata;
import jp.hakamap.project.domain.value.AreaId;
import jp.hakamap.project.domain.value.AssetId;
import jp.hakamap.project.domain.value.GraveId;
import jp.hakamap.project.domain.value.PersonId;
import jp.hakamap.project.domain.value.ProjectName;

final class ProjectStateTransitions {
  private ProjectStateTransitions() {}

  static ProjectAggregate applyAfter(ProjectAggregate current, ProjectChangeSet changeSet) {
    return apply(current, changeSet, true);
  }

  static ProjectAggregate applyBefore(ProjectAggregate current, ProjectChangeSet changeSet) {
    return apply(current, changeSet, false);
  }

  private static ProjectAggregate apply(
      ProjectAggregate current, ProjectChangeSet changeSet, boolean useAfter) {
    Map<AreaId, Area> areas = new LinkedHashMap<>(current.areas());
    Map<GraveId, Grave> graves = new LinkedHashMap<>(current.graves());
    Map<PersonId, Person> people = new LinkedHashMap<>(current.people());
    Map<AssetId, AssetMetadata> assets = new LinkedHashMap<>(current.assets());
    applyDeltas(areas, changeSet.areaDeltas(), useAfter);
    applyDeltas(graves, changeSet.graveDeltas(), useAfter);
    applyDeltas(people, changeSet.personDeltas(), useAfter);
    applyDeltas(assets, changeSet.assetDeltas(), useAfter);

    ProjectName name =
        changeSet
            .projectNameDelta()
            .map(delta -> selected(delta, useAfter).orElseThrow())
            .orElse(current.metadata().name());
    Optional<BackgroundPlacement> background =
        changeSet
            .backgroundDelta()
            .map(delta -> selected(delta, useAfter))
            .orElse(current.background());
    ProjectMetadata metadata =
        new ProjectMetadata(
            current.metadata().id(),
            name,
            current.metadata().createdAt(),
            current.metadata().updatedAt());
    return new ProjectAggregate(
        metadata, background, areas.values(), graves.values(), people.values(), assets.values());
  }

  private static <I, T> void applyDeltas(
      Map<I, T> values, Iterable<EntityDelta<I, T>> deltas, boolean useAfter) {
    for (EntityDelta<I, T> delta : deltas) {
      Optional<T> selected = useAfter ? delta.after() : delta.before();
      if (selected.isPresent()) {
        values.put(delta.id(), selected.orElseThrow());
      } else {
        values.remove(delta.id());
      }
    }
  }

  private static <T> Optional<T> selected(ValueDelta<T> delta, boolean useAfter) {
    return useAfter ? delta.after() : delta.before();
  }
}
