package jp.hakamap.persistence.json.model.project;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
  "id",
  "graveId",
  "name",
  "posthumousName",
  "createdAt",
  "updatedAt",
  "displayOrder"
})
public record PersonV1(
    UUID id,
    UUID graveId,
    String name,
    String posthumousName,
    @JsonFormat(pattern = "uuuu-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC") Instant createdAt,
    @JsonFormat(pattern = "uuuu-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC") Instant updatedAt,
    int displayOrder) {}
