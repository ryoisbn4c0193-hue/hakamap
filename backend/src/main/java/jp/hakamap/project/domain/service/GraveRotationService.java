package jp.hakamap.project.domain.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import jp.hakamap.project.domain.model.Grave;
import jp.hakamap.project.domain.value.DomainValidationException;
import jp.hakamap.project.domain.value.MapRectangle;
import jp.hakamap.project.domain.value.RotationDegrees;

/** 選択全体の外接矩形の中心を軸に、相対配置を保って墓所を回転する。 */
public final class GraveRotationService {
  private static final BigDecimal FULL_ROTATION = BigDecimal.valueOf(360);

  private final RotatedRectangleGeometry geometry = new RotatedRectangleGeometry();

  public List<Grave> rotate(List<Grave> graves, BigDecimal deltaDegrees, Instant timestamp) {
    if (graves.isEmpty() || deltaDegrees == null) {
      throw new DomainValidationException("invalid-rotation");
    }
    BigDecimal delta = normalize(deltaDegrees);
    if (delta.signum() == 0) {
      return List.copyOf(graves);
    }
    var bounds =
        graves.stream().map(grave -> geometry.bounds(grave.rectangle(), grave.rotation())).toList();
    double centerX =
        (bounds.stream().mapToDouble(RotatedRectangleGeometry.Bounds::minimumX).min().orElseThrow()
                + bounds.stream()
                    .mapToDouble(RotatedRectangleGeometry.Bounds::maximumX)
                    .max()
                    .orElseThrow())
            / 2;
    double centerY =
        (bounds.stream().mapToDouble(RotatedRectangleGeometry.Bounds::minimumY).min().orElseThrow()
                + bounds.stream()
                    .mapToDouble(RotatedRectangleGeometry.Bounds::maximumY)
                    .max()
                    .orElseThrow())
            / 2;
    double radians = Math.toRadians(delta.doubleValue());
    double cosine = Math.cos(radians);
    double sine = Math.sin(radians);
    return graves.stream()
        .map(
            grave -> {
              MapRectangle rectangle = grave.rectangle();
              double x =
                  rectangle.left().doubleValue()
                      + rectangle.size().width().doubleValue() / 2
                      - centerX;
              double y =
                  rectangle.top().doubleValue()
                      + rectangle.size().height().doubleValue() / 2
                      - centerY;
              MapRectangle target =
                  new MapRectangle(
                      BigDecimal.valueOf(
                          centerX
                              + x * cosine
                              - y * sine
                              - rectangle.size().width().doubleValue() / 2),
                      BigDecimal.valueOf(
                          centerY
                              + x * sine
                              + y * cosine
                              - rectangle.size().height().doubleValue() / 2),
                      rectangle.size().width(),
                      rectangle.size().height());
              return grave.transform(
                  target,
                  new RotationDegrees(normalize(grave.rotation().value().add(delta))),
                  timestamp);
            })
        .toList();
  }

  private BigDecimal normalize(BigDecimal degrees) {
    BigDecimal remainder = degrees.remainder(FULL_ROTATION);
    BigDecimal positive = remainder.signum() < 0 ? remainder.add(FULL_ROTATION) : remainder;
    BigDecimal rounded = positive.setScale(3, java.math.RoundingMode.HALF_UP);
    return rounded.remainder(FULL_ROTATION);
  }
}
