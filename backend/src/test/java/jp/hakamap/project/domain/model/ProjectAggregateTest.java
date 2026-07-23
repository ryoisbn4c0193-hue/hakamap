package jp.hakamap.project.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import jp.hakamap.project.domain.result.ProjectInvariantException;
import jp.hakamap.project.domain.service.NumberingAssignment;
import jp.hakamap.project.domain.value.AreaColorPreset;
import jp.hakamap.project.domain.value.AreaId;
import jp.hakamap.project.domain.value.AreaName;
import jp.hakamap.project.domain.value.AssetDisplayName;
import jp.hakamap.project.domain.value.AssetId;
import jp.hakamap.project.domain.value.AssetType;
import jp.hakamap.project.domain.value.DisplayOrder;
import jp.hakamap.project.domain.value.DomainWarningCode;
import jp.hakamap.project.domain.value.GraveCompletionStatus;
import jp.hakamap.project.domain.value.GraveId;
import jp.hakamap.project.domain.value.IncompleteReason;
import jp.hakamap.project.domain.value.ManagementNumber;
import jp.hakamap.project.domain.value.MapRectangle;
import jp.hakamap.project.domain.value.ProjectId;
import jp.hakamap.project.domain.value.ProjectName;
import jp.hakamap.project.domain.value.RotationDegrees;
import org.junit.jupiter.api.Test;

class ProjectAggregateTest {
  private static final Instant TIME = Instant.parse("2026-07-23T00:00:00Z");

  private static final Instant LATER = Instant.parse("2026-07-23T01:00:00Z");

  @Test
  void rejectsNormalizedAreaNamesColorsAndAreaOverlaps() {
    Area first = area(1, "第1くみ", "0", "0", "10", "10", AreaColorPreset.BLUE, 0);
    ProjectAggregate project = project(List.of(first), List.of());

    assertThatThrownBy(
            () ->
                project.addArea(area(2, " 第１くみ ", "10", "0", "10", "10", AreaColorPreset.GREEN, 1)))
        .isInstanceOf(ProjectInvariantException.class)
        .hasMessage("area-name-duplicate");
    assertThat(project.areas()).containsOnlyKeys(first.id());

    assertThatThrownBy(
            () -> project.addArea(area(3, "第二組", "10", "0", "10", "10", AreaColorPreset.BLUE, 1)))
        .hasMessage("area-color-in-use");
    assertThatThrownBy(
            () ->
                project.addArea(area(4, "第三組", "9.999", "0", "10", "10", AreaColorPreset.GREEN, 1)))
        .hasMessage("area-overlap");
  }

  @Test
  void derivesAllIncompleteReasonsAndOutsideWarningIndependently() {
    Area area = area(1, "一組", "0", "0", "10", "10", AreaColorPreset.BLUE, 0);
    Grave outside = grave(2, Optional.of("1"), "9", "0", "2", "2");
    Grave unassigned = grave(3, Optional.empty(), "20", "0", "2", "2");
    ProjectAggregate project = project(List.of(area), List.of(outside, unassigned));

    assertThat(project.graveStatus(outside.id()).completionStatus())
        .isEqualTo(GraveCompletionStatus.COMPLETE);
    assertThat(project.graveStatus(outside.id()).warnings())
        .containsExactly(DomainWarningCode.OUTSIDE_AREA_BOUNDS);
    assertThat(project.graveStatus(unassigned.id()).incompleteReasons())
        .containsExactlyInAnyOrder(IncompleteReason.UNASSIGNED, IncompleteReason.UNNUMBERED);
  }

  @Test
  void failedBatchMoveLeavesEveryGraveUnchanged() {
    Grave first = grave(1, Optional.empty(), "0", "0", "10", "10");
    Grave second = grave(2, Optional.empty(), "20", "0", "10", "10");
    ProjectAggregate project = project(List.of(), List.of(first, second));

    assertThatThrownBy(
            () ->
                project.moveGraves(
                    Map.of(
                        first.id(), rectangle("5", "0", "10", "10"),
                        second.id(), rectangle("10", "0", "10", "10")),
                    LATER))
        .isInstanceOf(ProjectInvariantException.class)
        .hasMessage("grave-overlap");
    assertThat(project.graves().get(first.id())).isEqualTo(first);
    assertThat(project.graves().get(second.id())).isEqualTo(second);
  }

  @Test
  void copyKeepsOnlyShapeAndIsAtomicWhenAnyTargetOverlaps() {
    Grave source =
        new Grave(
            new GraveId(uuid(1)),
            Optional.of(new ManagementNumber("A-1")),
            Optional.of(new jp.hakamap.project.domain.value.GraveName("墓所名")),
            Optional.of(new jp.hakamap.project.domain.value.GraveNotes("備考")),
            rectangle("0", "0", "10", "10"),
            new RotationDegrees(decimal("15")),
            TIME);
    ProjectAggregate project = project(List.of(), List.of(source));
    AtomicInteger ids = new AtomicInteger(100);

    Grave copy =
        project
            .copyGraves(
                List.of(source.id()),
                decimal("20"),
                BigDecimal.ZERO,
                LATER,
                () -> uuid(ids.getAndIncrement()))
            .getFirst();
    assertThat(copy.managementNumber()).isEmpty();
    assertThat(copy.name()).isEmpty();
    assertThat(copy.notes()).isEmpty();
    assertThat(copy.rotation()).isEqualTo(source.rotation());
    assertThat(copy.updatedAt()).isEqualTo(LATER);
    assertThat(project.graves().get(source.id())).isEqualTo(source);

    int countBeforeFailure = project.graves().size();
    assertThatThrownBy(
            () ->
                project.copyGraves(
                    List.of(source.id(), copy.id()),
                    decimal("5"),
                    BigDecimal.ZERO,
                    LATER,
                    () -> uuid(ids.getAndIncrement())))
        .hasMessage("grave-overlap");
    assertThat(project.graves()).hasSize(countBeforeFailure);
  }

  @Test
  void numberingIsAtomicAcrossAreasAndRejectsDuplicateBusinessKey() {
    Area area = area(1, "一組", "0", "0", "100", "100", AreaColorPreset.BLUE, 0);
    Grave existing = grave(2, Optional.of("A 1"), "0", "0", "10", "10");
    Grave target = grave(3, Optional.empty(), "20", "0", "10", "10");
    ProjectAggregate project = project(List.of(area), List.of(existing, target));

    assertThatThrownBy(
            () ->
                project.applyNumbering(
                    List.of(new NumberingAssignment(target.id(), new ManagementNumber("Ａ　１"))),
                    LATER))
        .hasMessage("grave-business-key-duplicate");
    assertThat(project.graves().get(target.id()).managementNumber()).isEmpty();
    assertThat(project.graves().get(target.id()).updatedAt()).isEqualTo(TIME);
  }

  @Test
  void deletingAreaReordersAndMakesGraveUnassignedWithoutDeletingIt() {
    Area first = area(1, "一組", "0", "0", "10", "10", AreaColorPreset.BLUE, 0);
    Area second = area(2, "二組", "20", "0", "10", "10", AreaColorPreset.GREEN, 1);
    Grave grave = grave(3, Optional.of("1"), "0", "0", "5", "5");
    ProjectAggregate project = project(List.of(first, second), List.of(grave));

    project.removeArea(first.id());

    assertThat(project.areas().get(second.id()).displayOrder()).isEqualTo(new DisplayOrder(0));
    assertThat(project.graves()).containsKey(grave.id());
    assertThat(project.graveStatus(grave.id()).incompleteReasons())
        .containsExactly(IncompleteReason.UNASSIGNED);
  }

  @Test
  void rejectsCrossTypeUuidAndMissingOwnerReferences() {
    Area area = area(1, "一組", "0", "0", "10", "10", AreaColorPreset.BLUE, 0);
    Grave sameId = grave(1, Optional.empty(), "20", "0", "5", "5");
    assertThatThrownBy(() -> project(List.of(area), List.of(sameId)))
        .hasMessage("duplicate-entity-id");

    Person orphan =
        new Person(
            new jp.hakamap.project.domain.value.PersonId(uuid(2)),
            new GraveId(uuid(404)),
            Optional.of(new jp.hakamap.project.domain.value.PersonName("人物")),
            Optional.empty(),
            TIME,
            TIME,
            new DisplayOrder(0));
    assertThatThrownBy(
            () ->
                new ProjectAggregate(
                    metadata(), Optional.empty(), List.of(), List.of(), List.of(orphan), List.of()))
        .hasMessage("missing-grave-reference");
  }

  @Test
  void rejectsNonContiguousOwnedOrderAndAttachmentLimit() {
    Grave grave = grave(1, Optional.empty(), "0", "0", "5", "5");
    Person person =
        new Person(
            new jp.hakamap.project.domain.value.PersonId(uuid(2)),
            grave.id(),
            Optional.of(new jp.hakamap.project.domain.value.PersonName("人物")),
            Optional.empty(),
            TIME,
            TIME,
            new DisplayOrder(1));
    assertThatThrownBy(
            () ->
                new ProjectAggregate(
                    metadata(),
                    Optional.empty(),
                    List.of(),
                    List.of(grave),
                    List.of(person),
                    List.of()))
        .hasMessage("invalid-person-display-order");

    List<AssetMetadata> attachments =
        java.util.stream.IntStream.range(0, 21)
            .mapToObj(index -> attachment(100 + index, grave.id(), index))
            .toList();
    assertThatThrownBy(
            () ->
                new ProjectAggregate(
                    metadata(),
                    Optional.empty(),
                    List.of(),
                    List.of(grave),
                    List.of(),
                    attachments))
        .hasMessage("asset-count-exceeded");
  }

  @Test
  void rejectsThirteenthAreaAndReportsNoAvailableColor() {
    List<Area> twelveAreas =
        java.util.stream.IntStream.range(0, 12)
            .mapToObj(
                index ->
                    area(
                        index + 1,
                        "エリア" + index,
                        Integer.toString(index * 20),
                        "0",
                        "10",
                        "10",
                        AreaColorPreset.values()[index],
                        index))
            .toList();
    ProjectAggregate project = project(twelveAreas, List.of());
    assertThatThrownBy(project::nextAvailableAreaColor).hasMessage("area-limit-exceeded");
    assertThatThrownBy(
            () -> project.addArea(area(20, "追加", "300", "0", "10", "10", AreaColorPreset.BLUE, 12)))
        .hasMessage("area-limit-exceeded");
  }

  private ProjectAggregate project(List<Area> areas, List<Grave> graves) {
    return new ProjectAggregate(metadata(), Optional.empty(), areas, graves, List.of(), List.of());
  }

  private ProjectMetadata metadata() {
    return new ProjectMetadata(new ProjectId(uuid(999)), new ProjectName("テスト"), TIME, TIME);
  }

  private AssetMetadata attachment(int id, GraveId graveId, int order) {
    return new AssetMetadata(
        new AssetId(uuid(id)),
        AssetType.ATTACHMENT,
        Optional.of(graveId),
        "attachment.png",
        "attachments/" + id + ".png",
        "image/png",
        "image/png",
        1,
        "0".repeat(64),
        TIME,
        Optional.of(new AssetDisplayName("添付" + id)),
        Optional.empty(),
        Optional.of(TIME),
        Optional.of(new DisplayOrder(order)));
  }

  private Area area(
      int id,
      String name,
      String x,
      String y,
      String width,
      String height,
      AreaColorPreset color,
      int order) {
    return new Area(
        new AreaId(uuid(id)),
        new AreaName(name),
        rectangle(x, y, width, height),
        color,
        true,
        new DisplayOrder(order));
  }

  private Grave grave(
      int id, Optional<String> number, String x, String y, String width, String height) {
    return new Grave(
        new GraveId(uuid(id)),
        number.map(ManagementNumber::new),
        Optional.empty(),
        Optional.empty(),
        rectangle(x, y, width, height),
        RotationDegrees.ZERO,
        TIME);
  }

  private MapRectangle rectangle(String x, String y, String width, String height) {
    return new MapRectangle(decimal(x), decimal(y), decimal(width), decimal(height));
  }

  private BigDecimal decimal(String value) {
    return new BigDecimal(value);
  }

  private UUID uuid(int value) {
    return UUID.fromString("00000000-0000-4000-8000-" + String.format("%012d", value));
  }
}
