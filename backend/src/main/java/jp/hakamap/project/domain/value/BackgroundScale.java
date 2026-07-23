package jp.hakamap.project.domain.value;

import java.math.BigDecimal;

public record BackgroundScale(BigDecimal value) {
  public BackgroundScale(BigDecimal value) {
    this.value = MapDecimal.positive(value, "invalid-background-scale");
  }
}
