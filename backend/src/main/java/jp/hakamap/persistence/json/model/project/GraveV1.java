package jp.hakamap.persistence.json.model.project;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
  "id",
  "managementNumber",
  "name",
  "notes",
  "x",
  "y",
  "width",
  "height",
  "rotation",
  "updatedAt"
})
public record GraveV1(
    UUID id,
    String managementNumber,
    String name,
    String notes,
    BigDecimal x,
    BigDecimal y,
    BigDecimal width,
    BigDecimal height,
    BigDecimal rotation,
    @JsonFormat(pattern = "uuuu-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC") Instant updatedAt) {}
