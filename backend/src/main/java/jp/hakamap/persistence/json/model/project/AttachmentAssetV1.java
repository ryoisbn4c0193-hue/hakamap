package jp.hakamap.persistence.json.model.project;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
  "id",
  "assetType",
  "originalFileName",
  "relativePath",
  "sourceMediaType",
  "storedMediaType",
  "sizeBytes",
  "sha256",
  "createdAt",
  "graveId",
  "displayName",
  "description",
  "updatedAt",
  "displayOrder"
})
public record AttachmentAssetV1(
    UUID id,
    String assetType,
    String originalFileName,
    String relativePath,
    String sourceMediaType,
    String storedMediaType,
    long sizeBytes,
    String sha256,
    @JsonFormat(pattern = "uuuu-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC") Instant createdAt,
    UUID graveId,
    String displayName,
    String description,
    @JsonFormat(pattern = "uuuu-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC") Instant updatedAt,
    int displayOrder)
    implements AssetV1 {}
