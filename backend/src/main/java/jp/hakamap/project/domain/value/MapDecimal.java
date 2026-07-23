package jp.hakamap.project.domain.value;

import java.math.BigDecimal;
import java.math.RoundingMode;

final class MapDecimal {
  static final int SCALE = 3;

  private MapDecimal() {}

  static BigDecimal normalize(BigDecimal value, String code) {
    if (value == null) {
      throw new DomainValidationException(code);
    }
    return value.setScale(SCALE, RoundingMode.HALF_UP);
  }

  static BigDecimal positive(BigDecimal value, String code) {
    BigDecimal normalized = normalize(value, code);
    if (normalized.signum() <= 0) {
      throw new DomainValidationException(code);
    }
    return normalized;
  }
}
