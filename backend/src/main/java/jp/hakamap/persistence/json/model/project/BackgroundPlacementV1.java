package jp.hakamap.persistence.json.model.project;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.math.BigDecimal;
import java.util.UUID;

@JsonPropertyOrder({"assetId", "x", "y", "rotation", "scaleX", "scaleY"})
public record BackgroundPlacementV1(
    UUID assetId,
    BigDecimal x,
    BigDecimal y,
    BigDecimal rotation,
    BigDecimal scaleX,
    BigDecimal scaleY) {}
