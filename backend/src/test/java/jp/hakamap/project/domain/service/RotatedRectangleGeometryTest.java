package jp.hakamap.project.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import jp.hakamap.project.domain.value.MapPoint;
import jp.hakamap.project.domain.value.MapRectangle;
import jp.hakamap.project.domain.value.RotationDegrees;
import org.junit.jupiter.api.Test;

class RotatedRectangleGeometryTest {
  private final RotatedRectangleGeometry geometry = new RotatedRectangleGeometry();

  @Test
  void allowsEdgeContactButRejectsAreaOverlapAfterRotation() {
    MapRectangle first = rectangle(0, 0, 10, 4);
    MapRectangle touching = rectangle(0, 10, 10, 4);
    MapRectangle overlapping = rectangle(0, 9, 10, 4);

    assertThat(geometry.overlapsArea(first, rotation(90), touching, rotation(90))).isFalse();
    assertThat(geometry.overlapsArea(first, rotation(90), overlapping, rotation(90))).isTrue();
  }

  @Test
  void usesRotatedOutlineForPointAndRectangleContainment() {
    MapRectangle area = rectangle(0, 0, 20, 10);
    assertThat(
            geometry.containsClosed(
                area, rotation(90), new MapPoint(BigDecimal.TEN, BigDecimal.valueOf(14))))
        .isTrue();
    assertThat(
            geometry.containsClosed(
                area, rotation(90), new MapPoint(BigDecimal.valueOf(19), BigDecimal.valueOf(5))))
        .isFalse();
    assertThat(geometry.containsClosed(area, rotation(90), rectangle(8, 2, 4, 6), rotation(45)))
        .isTrue();
  }

  private MapRectangle rectangle(long x, long y, long width, long height) {
    return new MapRectangle(
        BigDecimal.valueOf(x),
        BigDecimal.valueOf(y),
        BigDecimal.valueOf(width),
        BigDecimal.valueOf(height));
  }

  private RotationDegrees rotation(long degrees) {
    return new RotationDegrees(BigDecimal.valueOf(degrees));
  }
}
