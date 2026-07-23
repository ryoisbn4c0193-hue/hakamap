package jp.hakamap.project.domain.value;

import java.math.BigDecimal;

public record MapPoint(BigDecimal x, BigDecimal y) {
  public MapPoint(BigDecimal x, BigDecimal y) {
    this.x = MapDecimal.normalize(x, "invalid-map-point");
    this.y = MapDecimal.normalize(y, "invalid-map-point");
  }

  public MapPoint translate(BigDecimal deltaX, BigDecimal deltaY) {
    return new MapPoint(x.add(deltaX), y.add(deltaY));
  }
}
