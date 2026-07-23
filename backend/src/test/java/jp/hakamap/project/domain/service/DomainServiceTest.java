package jp.hakamap.project.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import jp.hakamap.project.domain.model.Area;
import jp.hakamap.project.domain.model.Grave;
import jp.hakamap.project.domain.result.ProjectInvariantException;
import jp.hakamap.project.domain.value.AreaColorPreset;
import jp.hakamap.project.domain.value.AreaId;
import jp.hakamap.project.domain.value.AreaName;
import jp.hakamap.project.domain.value.DisplayOrder;
import jp.hakamap.project.domain.value.GraveId;
import jp.hakamap.project.domain.value.MapPoint;
import jp.hakamap.project.domain.value.MapRectangle;
import jp.hakamap.project.domain.value.MapSize;
import jp.hakamap.project.domain.value.RotationDegrees;
import org.junit.jupiter.api.Test;

class DomainServiceTest {
  private static final Instant TIME = Instant.parse("2026-07-23T00:00:00Z");

  @Test
  void comparisonKeyUnifiesWidthCaseWhitespaceAndKana() {
    TextNormalizationService service = new TextNormalizationService();
    assertThat(service.comparisonKey(" Ａ b　カ ").value())
        .isEqualTo(service.comparisonKey("aBか").value());
  }

  @Test
  void membershipUsesLowestDisplayOrderOnSharedBoundaryEvenWhenHidden() {
    Area second = area(2, "右", "10", 1, false);
    Area first = area(1, "左", "0", 0, false);
    Grave grave = grave(10, "9", "0", "2", "2");

    assertThat(new AreaMembershipResolver().resolve(grave, List.of(second, first))).contains(first);
  }

  @Test
  void matrixAndFillGenerateInRowMajorOrderWithSameTimestamp() {
    GraveGenerationService service = new GraveGenerationService();
    AtomicInteger ids = new AtomicInteger();
    UuidSource source = () -> uuid(100 + ids.getAndIncrement());

    List<Grave> matrix =
        service.matrix(
            2,
            2,
            new MapPoint(decimal("10"), decimal("20")),
            new MapSize(decimal("3"), decimal("4")),
            decimal("1"),
            decimal("2"),
            TIME,
            source);
    assertThat(matrix)
        .extracting(grave -> grave.rectangle().topLeft())
        .containsExactly(
            new MapPoint(decimal("10"), decimal("20")),
            new MapPoint(decimal("14"), decimal("20")),
            new MapPoint(decimal("10"), decimal("26")),
            new MapPoint(decimal("14"), decimal("26")));
    assertThat(matrix).extracting(Grave::updatedAt).containsOnly(TIME);

    List<Grave> filled =
        service.fill(
            rectangle("0", "0", "7", "4"),
            new MapSize(decimal("3"), decimal("4")),
            decimal("1"),
            decimal("0"),
            TIME,
            source);
    assertThat(filled).hasSize(2);
  }

  @Test
  void visualRowsIncludeExactlyFiftyPercentOverlapWithoutChainMerging() {
    NumberingService service = new NumberingService();
    Grave first = grave(1, "20", "0", "10", "10");
    Grave sameRow = grave(2, "0", "5", "10", "10");
    Grave chainedOnly = grave(3, "10", "10", "10", "10");

    List<NumberingAssignment> assignments =
        service.preview(
            List.of(first, sameRow, chainedOnly),
            new NumberingRequest("A-", BigInteger.valueOf(8), 3, ""));

    assertThat(assignments)
        .extracting(NumberingAssignment::graveId)
        .containsExactly(sameRow.id(), first.id(), chainedOnly.id());
    assertThat(assignments)
        .extracting(assignment -> assignment.managementNumber().value())
        .containsExactly("A-008", "A-009", "A-010");
  }

  @Test
  void numberingRejectsExistingNumberWithoutProducingPartialPlan() {
    Grave numbered =
        new Grave(
            new GraveId(uuid(1)),
            Optional.of(new jp.hakamap.project.domain.value.ManagementNumber("1")),
            Optional.empty(),
            Optional.empty(),
            rectangle("0", "0", "10", "10"),
            RotationDegrees.ZERO,
            TIME);
    assertThatThrownBy(
            () ->
                new NumberingService()
                    .preview(
                        List.of(numbered, grave(2, "20", "0", "10", "10")),
                        new NumberingRequest("", BigInteger.ZERO, 1, "")))
        .isInstanceOf(ProjectInvariantException.class)
        .hasMessage("grave-number-already-set");
  }

  private Area area(int id, String name, String x, int order, boolean visible) {
    return new Area(
        new AreaId(uuid(id)),
        new AreaName(name),
        rectangle(x, "0", "10", "10"),
        AreaColorPreset.values()[order],
        visible,
        new DisplayOrder(order));
  }

  private Grave grave(int id, String x, String y, String width, String height) {
    return new Grave(
        new GraveId(uuid(id)),
        Optional.empty(),
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
