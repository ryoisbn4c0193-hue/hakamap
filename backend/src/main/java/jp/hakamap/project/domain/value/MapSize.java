package jp.hakamap.project.domain.value;

import java.math.BigDecimal;

public record MapSize(BigDecimal width, BigDecimal height) {
  public MapSize(BigDecimal width, BigDecimal height) {
    this.width = MapDecimal.positive(width, "invalid-map-size");
    this.height = MapDecimal.positive(height, "invalid-map-size");
  }
}
