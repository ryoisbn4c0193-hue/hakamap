package jp.hakamap.project.domain.model;

import java.time.Instant;
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
    Objects.requireNonNull(relativePath, "relativePath");
    Objects.requireNonNull(sourceMediaType, "sourceMediaType");
    Objects.requireNonNull(storedMediaType, "storedMediaType");
    Objects.requireNonNull(sha256, "sha256");
    Objects.requireNonNull(createdAt, "createdAt");
    displayName = ListCopies.optional(displayName);
    description = ListCopies.optional(description);
    updatedAt = ListCopies.optional(updatedAt);
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
