package jp.hakamap.project.domain.model;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;
import jp.hakamap.project.domain.value.AssetDescription;
import jp.hakamap.project.domain.value.AssetDisplayName;
import jp.hakamap.project.domain.value.AssetId;
import jp.hakamap.project.domain.value.AssetType;
import jp.hakamap.project.domain.value.DisplayOrder;
import jp.hakamap.project.domain.value.GraveId;

public record AssetMetadata(
    AssetId id,
    AssetType type,
    Optional<GraveId> graveId,
    String originalFileName,
    String relativePath,
    String sourceMediaType,
    String storedMediaType,
    long sizeBytes,
    String sha256,
    Instant createdAt,
    Optional<AssetDisplayName> displayName,
    Optional<AssetDescription> description,
    Optional<Instant> updatedAt,
    Optional<DisplayOrder> displayOrder) {
  public AssetMetadata {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(type, "type");
    graveId = ListCopies.optional(graveId);
    Objects.requireNonNull(originalFileName, "originalFileName");
    Objects.requireNonNull(relativePath, "relativePath");
    Objects.requireNonNull(sourceMediaType, "sourceMediaType");
    Objects.requireNonNull(storedMediaType, "storedMediaType");
    Objects.requireNonNull(sha256, "sha256");
    createdAt = Objects.requireNonNull(createdAt, "createdAt").truncatedTo(ChronoUnit.MILLIS);
    displayName = ListCopies.optional(displayName);
    description = ListCopies.optional(description);
    updatedAt = ListCopies.optional(updatedAt);
    updatedAt = updatedAt.map(value -> value.truncatedTo(ChronoUnit.MILLIS));
    displayOrder = ListCopies.optional(displayOrder);
    if (sizeBytes <= 0) {
      throw new IllegalArgumentException("invalid-asset-size");
    }
    if (type == AssetType.BACKGROUND && (graveId.isPresent() || displayOrder.isPresent())) {
      throw new IllegalArgumentException("invalid-background-asset-owner");
    }
    if (type == AssetType.ATTACHMENT
        && (graveId.isEmpty()
            || displayName.isEmpty()
            || updatedAt.isEmpty()
            || displayOrder.isEmpty())) {
      throw new IllegalArgumentException("invalid-attachment-metadata");
    }
  }
}
