package jp.hakamap.persistence.json.model.project;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.math.BigDecimal;
import java.util.UUID;

@JsonPropertyOrder({
  "id",
  "name",
  "x",
  "y",
  "width",
  "height",
  "colorPreset",
  "visible",
  "displayOrder"
})
public record AreaV1(
    UUID id,
    String name,
    BigDecimal x,
    BigDecimal y,
    BigDecimal width,
    BigDecimal height,
    String colorPreset,
    boolean visible,
    int displayOrder) {}
