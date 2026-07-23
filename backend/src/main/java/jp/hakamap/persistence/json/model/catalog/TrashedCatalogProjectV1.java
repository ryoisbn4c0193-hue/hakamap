package jp.hakamap.persistence.json.model.catalog;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.time.Instant;
import java.util.UUID;

@JsonPropertyOrder({
  "projectId",
  "path",
  "originalPath",
  "lastKnownName",
  "lastKnownCreatedAt",
  "lastKnownUpdatedAt",
  "state"
})
public record TrashedCatalogProjectV1(
    UUID projectId,
    String path,
    String originalPath,
    String lastKnownName,
    @JsonFormat(pattern = "uuuu-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
        Instant lastKnownCreatedAt,
    @JsonFormat(pattern = "uuuu-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
        Instant lastKnownUpdatedAt,
    String state)
    implements CatalogProjectV1 {}
