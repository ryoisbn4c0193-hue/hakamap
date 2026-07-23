package jp.hakamap.project.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import jp.hakamap.project.domain.result.ProjectInvariantException;
import jp.hakamap.project.domain.service.AreaMembershipResolver;
import jp.hakamap.project.domain.service.GeometryConstraintService;
import jp.hakamap.project.domain.service.GraveGenerationService;
import jp.hakamap.project.domain.service.GraveStatusDeriver;
import jp.hakamap.project.domain.service.NumberingAssignment;
import jp.hakamap.project.domain.service.ProjectInvariantValidator;
import jp.hakamap.project.domain.service.TextNormalizationService;
import jp.hakamap.project.domain.service.UuidSource;
import jp.hakamap.project.domain.value.AreaColorPreset;
import jp.hakamap.project.domain.value.AreaId;
import jp.hakamap.project.domain.value.AssetId;
import jp.hakamap.project.domain.value.DisplayOrder;
import jp.hakamap.project.domain.value.GraveId;
import jp.hakamap.project.domain.value.MapRectangle;
import jp.hakamap.project.domain.value.PersonId;

public final class ProjectAggregate {
  private final ProjectInvariantValidator validator;

  private final GraveStatusDeriver statusDeriver;

  private final GraveGenerationService generationService;

  private ProjectMetadata metadata;

  private Optional<BackgroundPlacement> background;

  private Map<AreaId, Area> areas;

  private Map<GraveId, Grave> graves;

  private Map<PersonId, Person> people;

  private Map<AssetId, AssetMetadata> assets;

  public ProjectAggregate(
      ProjectMetadata metadata,
      Optional<BackgroundPlacement> background,
      Collection<Area> areas,
      Collection<Grave> graves,
      Collection<Person> people,
      Collection<AssetMetadata> assets) {
    AreaMembershipResolver membershipResolver = new AreaMembershipResolver();
    this.validator =
        new ProjectInvariantValidator(
            new TextNormalizationService(), membershipResolver, new GeometryConstraintService());
    this.statusDeriver = new GraveStatusDeriver(membershipResolver);
    this.generationService = new GraveGenerationService();
    this.metadata = java.util.Objects.requireNonNull(metadata, "metadata");
    this.background = java.util.Objects.requireNonNull(background, "background");
    this.areas = indexed(areas, Area::id, "duplicate-area-id");
    this.graves = indexed(graves, Grave::id, "duplicate-grave-id");
    this.people = indexed(people, Person::id, "duplicate-person-id");
    this.assets = indexed(assets, AssetMetadata::id, "duplicate-asset-id");
    validateCurrent();
  }

  public ProjectMetadata metadata() {
    return metadata;
  }

  public Optional<BackgroundPlacement> background() {
    return background;
  }

  public Map<AreaId, Area> areas() {
    return Map.copyOf(areas);
  }

  public Map<GraveId, Grave> graves() {
    return Map.copyOf(graves);
  }

  public Map<PersonId, Person> people() {
    return Map.copyOf(people);
  }

  public Map<AssetId, AssetMetadata> assets() {
    return Map.copyOf(assets);
  }

  public GraveStatus graveStatus(GraveId graveId) {
    return statusDeriver.derive(requireGrave(graveId), areas.values());
  }

  public void addArea(Area area) {
    Map<AreaId, Area> candidate = new LinkedHashMap<>(areas);
    if (candidate.putIfAbsent(area.id(), area) != null) {
      throw new ProjectInvariantException("duplicate-area-id");
    }
    validate(background, candidate, graves, people, assets);
    areas = candidate;
  }

  public AreaColorPreset nextAvailableAreaColor() {
    Set<AreaColorPreset> used =
        areas.values().stream().map(Area::color).collect(Collectors.toSet());
    return java.util.Arrays.stream(AreaColorPreset.values())
        .filter(color -> !used.contains(color))
        .findFirst()
        .orElseThrow(() -> new ProjectInvariantException("area-limit-exceeded"));
  }

  public void removeArea(AreaId areaId) {
    if (!areas.containsKey(areaId)) {
      throw new ProjectInvariantException("area-not-found");
    }
    List<Area> ordered =
        areas.values().stream()
            .filter(area -> !area.id().equals(areaId))
            .sorted(java.util.Comparator.comparing(Area::displayOrder))
            .toList();
    Map<AreaId, Area> candidate = new LinkedHashMap<>();
    for (int index = 0; index < ordered.size(); index++) {
      Area area = ordered.get(index).withDisplayOrder(new DisplayOrder(index));
      candidate.put(area.id(), area);
    }
    validate(background, candidate, graves, people, assets);
    areas = candidate;
  }

  public void addGraves(Collection<Grave> newGraves) {
    Map<GraveId, Grave> candidate = new LinkedHashMap<>(graves);
    for (Grave grave : newGraves) {
      if (candidate.putIfAbsent(grave.id(), grave) != null) {
        throw new ProjectInvariantException("duplicate-grave-id");
      }
    }
    validate(background, areas, candidate, people, assets);
    graves = candidate;
  }

  public void moveGraves(Map<GraveId, MapRectangle> targets, Instant commandTime) {
    if (targets.isEmpty()) {
      throw new ProjectInvariantException("grave-selection-required");
    }
    Map<GraveId, Grave> candidate = new LinkedHashMap<>(graves);
    targets.forEach(
        (id, rectangle) -> candidate.put(id, requireGrave(id).move(rectangle, commandTime)));
    validate(background, areas, candidate, people, assets);
    graves = candidate;
  }

  public List<Grave> copyGraves(
      Collection<GraveId> sourceIds,
      BigDecimal deltaX,
      BigDecimal deltaY,
      Instant commandTime,
      UuidSource uuidSource) {
    List<Grave> sources = sourceIds.stream().map(this::requireGrave).toList();
    List<Grave> copies = generationService.copies(sources, deltaX, deltaY, commandTime, uuidSource);
    addGraves(copies);
    return copies;
  }

  public void deleteGraves(Collection<GraveId> graveIds) {
    if (graveIds.isEmpty()) {
      throw new ProjectInvariantException("grave-selection-required");
    }
    graveIds.forEach(this::requireGrave);
    Map<GraveId, Grave> candidateGraves = new LinkedHashMap<>(graves);
    graveIds.forEach(candidateGraves::remove);
    Map<PersonId, Person> candidatePeople =
        people.values().stream()
            .filter(person -> !graveIds.contains(person.graveId()))
            .collect(
                Collectors.toMap(
                    Person::id, Function.identity(), (first, second) -> first, LinkedHashMap::new));
    Map<AssetId, AssetMetadata> candidateAssets =
        assets.values().stream()
            .filter(
                asset ->
                    asset.graveId().isEmpty() || !graveIds.contains(asset.graveId().orElseThrow()))
            .collect(
                Collectors.toMap(
                    AssetMetadata::id,
                    Function.identity(),
                    (first, second) -> first,
                    LinkedHashMap::new));
    validate(background, areas, candidateGraves, candidatePeople, candidateAssets);
    graves = candidateGraves;
    people = candidatePeople;
    assets = candidateAssets;
  }

  public void applyNumbering(List<NumberingAssignment> assignments, Instant commandTime) {
    if (assignments.isEmpty()) {
      throw new ProjectInvariantException("grave-selection-required");
    }
    Set<GraveId> uniqueIds =
        assignments.stream().map(NumberingAssignment::graveId).collect(Collectors.toSet());
    if (uniqueIds.size() != assignments.size()) {
      throw new ProjectInvariantException("duplicate-numbering-target");
    }
    Map<GraveId, Grave> candidate = new LinkedHashMap<>(graves);
    for (NumberingAssignment assignment : assignments) {
      Grave grave = requireGrave(assignment.graveId());
      if (grave.managementNumber().isPresent()) {
        throw new ProjectInvariantException("grave-number-already-set");
      }
      if (statusDeriver.derive(grave, areas.values()).areaId().isEmpty()) {
        throw new ProjectInvariantException("grave-unassigned-for-numbering");
      }
      candidate.put(grave.id(), grave.number(assignment.managementNumber(), commandTime));
    }
    validate(background, areas, candidate, people, assets);
    graves = candidate;
  }

  private Grave requireGrave(GraveId id) {
    Grave grave = graves.get(id);
    if (grave == null) {
      throw new ProjectInvariantException("grave-not-found");
    }
    return grave;
  }

  private void validateCurrent() {
    validate(background, areas, graves, people, assets);
  }

  private void validate(
      Optional<BackgroundPlacement> candidateBackground,
      Map<AreaId, Area> candidateAreas,
      Map<GraveId, Grave> candidateGraves,
      Map<PersonId, Person> candidatePeople,
      Map<AssetId, AssetMetadata> candidateAssets) {
    validator.validate(
        metadata.id(),
        candidateBackground,
        candidateAreas.values(),
        candidateGraves.values(),
        candidatePeople.values(),
        candidateAssets.values());
  }

  private static <K, V> Map<K, V> indexed(
      Collection<V> values, Function<V, K> key, String duplicateCode) {
    Map<K, V> result = new LinkedHashMap<>();
    for (V value : values) {
      if (result.putIfAbsent(key.apply(value), value) != null) {
        throw new ProjectInvariantException(duplicateCode);
      }
    }
    return result;
  }
}
