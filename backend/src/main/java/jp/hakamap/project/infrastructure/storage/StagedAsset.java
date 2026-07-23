package jp.hakamap.project.infrastructure.storage;

import java.nio.file.Path;
import java.util.Objects;
import jp.hakamap.project.domain.model.AssetMetadata;

public record StagedAsset(Path source, AssetMetadata metadata) {
  public StagedAsset {
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(metadata, "metadata");
  }
}
