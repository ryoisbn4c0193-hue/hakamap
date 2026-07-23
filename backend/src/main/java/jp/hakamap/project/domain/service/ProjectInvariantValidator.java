package jp.hakamap.project.domain.service;

import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import jp.hakamap.project.domain.model.Area;
import jp.hakamap.project.domain.model.AssetMetadata;
import jp.hakamap.project.domain.model.BackgroundPlacement;
import jp.hakamap.project.domain.model.Grave;
import jp.hakamap.project.domain.model.Person;
import jp.hakamap.project.domain.result.ProjectInvariantException;
import jp.hakamap.project.domain.value.AssetType;
import jp.hakamap.project.domain.value.ProjectId;
import jp.hakamap.project.domain.value.TextComparisonKey;

public final class ProjectInvariantValidator {
  private static final int MAXIMUM_AREAS = 12;

  private static final int MAXIMUM_ATTACHMENTS_PER_GRAVE = 20;

  private final TextNormalizationService textNormalization;

  private final AreaMembershipResolver membershipResolver;

  private final GeometryConstraintService geometry;

  public ProjectInvariantValidator(
      TextNormalizationService textNormalization,
      AreaMembershipResolver membershipResolver,
      GeometryConstraintService geometry) {
    this.textNormalization = textNormalization;
    this.membershipResolver = membershipResolver;
    this.geometry = geometry;
  }

  public void validate(
      ProjectId projectId,
      Optional<BackgroundPlacement> background,
      Collection<Area> areas,
      Collection<Grave> graves,
      Collection<Person> people,
      Collection<AssetMetadata> assets) {
    if (areas.size() > MAXIMUM_AREAS) {
      fail("area-limit-exceeded");
    }
    requireUniqueAcrossEntityTypes(projectId, areas, graves, people, assets);
    requireContiguous(areas, area -> area.displayOrder().value(), "invalid-area-display-order");
    requireUnique(
        areas, area -> textNormalization.comparisonKey(area.name().value()), "area-name-duplicate");
    requireUnique(areas, Area::color, "area-color-in-use");
    geometry.requireNoAreaOverlap(areas, Area::rectangle, "area-overlap");
    geometry.requireNoAreaOverlap(graves, Grave::rectangle, "grave-overlap");

    Set<jp.hakamap.project.domain.value.GraveId> graveIds =
        graves.stream().map(Grave::id).collect(Collectors.toUnmodifiableSet());
    if (people.stream().anyMatch(person -> !graveIds.contains(person.graveId()))
        || assets.stream()
            .filter(asset -> asset.type() == AssetType.ATTACHMENT)
            .anyMatch(
                asset ->
                    asset.graveId().isEmpty()
                        || !graveIds.contains(asset.graveId().orElseThrow()))) {
      fail("missing-grave-reference");
    }
    requireOwnedOrders(
        people,
        Person::graveId,
        person -> person.displayOrder().value(),
        "invalid-person-display-order");
    requireOwnedOrders(
        assets.stream().filter(asset -> asset.type() == AssetType.ATTACHMENT).toList(),
        asset -> asset.graveId().orElseThrow(),
        asset -> asset.displayOrder().orElseThrow().value(),
        "invalid-attachment-display-order");
    for (var graveId : graveIds) {
      long count =
          assets.stream()
              .filter(asset -> asset.type() == AssetType.ATTACHMENT)
              .filter(asset -> asset.graveId().orElseThrow().equals(graveId))
              .count();
      if (count > MAXIMUM_ATTACHMENTS_PER_GRAVE) {
        fail("asset-count-exceeded");
      }
    }

    Set<jp.hakamap.project.domain.value.AssetId> backgroundAssets =
        assets.stream()
            .filter(asset -> asset.type() == AssetType.BACKGROUND)
            .map(AssetMetadata::id)
            .collect(Collectors.toUnmodifiableSet());
    if (background.isPresent() && !backgroundAssets.contains(background.orElseThrow().assetId())) {
      fail("missing-background-asset");
    }
    requireUniqueBusinessKeys(areas, graves);
  }

  private void requireUniqueBusinessKeys(Collection<Area> areas, Collection<Grave> graves) {
    Set<String> keys = new HashSet<>();
    for (Grave grave : graves) {
      Optional<Area> area = membershipResolver.resolve(grave, areas);
      if (area.isPresent() && grave.managementNumber().isPresent()) {
        TextComparisonKey number =
            textNormalization.comparisonKey(grave.managementNumber().orElseThrow().value());
        String key = area.orElseThrow().id().value() + ":" + number.value();
        if (!keys.add(key)) {
          fail("grave-business-key-duplicate");
        }
      }
    }
  }

  private void requireUniqueAcrossEntityTypes(
      ProjectId projectId,
      Collection<Area> areas,
      Collection<Grave> graves,
      Collection<Person> people,
      Collection<AssetMetadata> assets) {
    Set<UUID> ids = new HashSet<>();
    requireNew(ids, projectId.value());
    areas.forEach(area -> requireNew(ids, area.id().value()));
    graves.forEach(grave -> requireNew(ids, grave.id().value()));
    people.forEach(person -> requireNew(ids, person.id().value()));
    assets.forEach(asset -> requireNew(ids, asset.id().value()));
  }

  private void requireNew(Set<UUID> ids, UUID id) {
    if (!ids.add(id)) {
      fail("duplicate-entity-id");
    }
  }

  private <T, K> void requireUnique(Collection<T> values, Function<T, K> key, String code) {
    Set<K> keys = new HashSet<>();
    if (values.stream().map(key).anyMatch(value -> !keys.add(value))) {
      fail(code);
    }
  }

  private <T> void requireContiguous(
      Collection<T> values, Function<T, Integer> order, String code) {
    Set<Integer> actual = values.stream().map(order).collect(Collectors.toSet());
    for (int expected = 0; expected < values.size(); expected++) {
      if (!actual.contains(expected)) {
        fail(code);
      }
    }
  }

  private <T, O> void requireOwnedOrders(
      Collection<T> values, Function<T, O> owner, Function<T, Integer> order, String code) {
    values.stream()
        .collect(Collectors.groupingBy(owner))
        .values()
        .forEach(owned -> requireContiguous(owned, order, code));
  }

  private void fail(String code) {
    throw new ProjectInvariantException(code);
  }
}
