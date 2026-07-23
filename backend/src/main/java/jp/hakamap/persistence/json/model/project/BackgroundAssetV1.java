package jp.hakamap.persistence.json.model.project;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.time.Instant;
import java.util.UUID;

@JsonPropertyOrder({
  "id",
  "assetType",
  "originalFileName",
  "relativePath",
  "sourceMediaType",
  "storedMediaType",
  "sizeBytes",
  "sha256",
  "createdAt"
})
public record BackgroundAssetV1(
    UUID id,
    String assetType,
    String originalFileName,
    String relativePath,
    String sourceMediaType,
    String storedMediaType,
    long sizeBytes,
    String sha256,
    @JsonFormat(pattern = "uuuu-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC") Instant createdAt)
    implements AssetV1 {}
