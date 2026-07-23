package jp.hakamap.project.domain.value;

import java.math.BigDecimal;

public record RotationDegrees(BigDecimal value) {
  private static final BigDecimal FULL_ROTATION = BigDecimal.valueOf(360);

  public static final RotationDegrees ZERO = new RotationDegrees(BigDecimal.ZERO);

  public RotationDegrees(BigDecimal value) {
    BigDecimal normalized = MapDecimal.normalize(value, "invalid-rotation");
    if (normalized.signum() < 0 || normalized.compareTo(FULL_ROTATION) >= 0) {
      throw new DomainValidationException("invalid-rotation");
    }
    this.value = normalized;
  }
}
