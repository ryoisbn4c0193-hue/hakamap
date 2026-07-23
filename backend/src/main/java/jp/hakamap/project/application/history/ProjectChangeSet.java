package jp.hakamap.project.application.history;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import jp.hakamap.project.domain.model.Area;
import jp.hakamap.project.domain.model.AssetMetadata;
import jp.hakamap.project.domain.model.BackgroundPlacement;
import jp.hakamap.project.domain.model.Grave;
import jp.hakamap.project.domain.model.Person;
import jp.hakamap.project.domain.value.AreaId;
import jp.hakamap.project.domain.value.AssetId;
import jp.hakamap.project.domain.value.GraveId;
import jp.hakamap.project.domain.value.PersonId;
import jp.hakamap.project.domain.value.ProjectName;

public record ProjectChangeSet(
    CommandId commandId,
    CommandType commandType,
    Instant commandTimestamp,
    List<EntityDelta<AreaId, Area>> areaDeltas,
    List<EntityDelta<GraveId, Grave>> graveDeltas,
    List<EntityDelta<PersonId, Person>> personDeltas,
    List<EntityDelta<AssetId, AssetMetadata>> assetDeltas,
    Optional<ValueDelta<ProjectName>> projectNameDelta,
    Optional<ValueDelta<BackgroundPlacement>> backgroundDelta,
    Set<AssetId> retainedAssetIds) {
  public ProjectChangeSet {
    Objects.requireNonNull(commandId, "commandId");
    Objects.requireNonNull(commandType, "commandType");
    Objects.requireNonNull(commandTimestamp, "commandTimestamp");
    areaDeltas = List.copyOf(areaDeltas);
    graveDeltas = List.copyOf(graveDeltas);
    personDeltas = List.copyOf(personDeltas);
    assetDeltas = List.copyOf(assetDeltas);
    projectNameDelta = Objects.requireNonNull(projectNameDelta, "projectNameDelta");
    backgroundDelta = Objects.requireNonNull(backgroundDelta, "backgroundDelta");
    retainedAssetIds = Set.copyOf(retainedAssetIds);
    int targets =
        areaDeltas.size()
            + graveDeltas.size()
            + personDeltas.size()
            + assetDeltas.size()
            + (projectNameDelta.isPresent() ? 1 : 0)
            + (backgroundDelta.isPresent() ? 1 : 0);
    if (targets == 0) {
      throw new IllegalArgumentException("empty-project-change-set");
    }
  }

  public int targetCount() {
    return areaDeltas.size()
        + graveDeltas.size()
        + personDeltas.size()
        + assetDeltas.size()
        + (projectNameDelta.isPresent() ? 1 : 0)
        + (backgroundDelta.isPresent() ? 1 : 0);
  }
}
