package jp.hakamap.project.application.editing;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import jp.hakamap.persistence.json.model.recovery.StagedAssetV1;
import jp.hakamap.project.domain.model.AssetMetadata;
import jp.hakamap.project.domain.model.ProjectAggregate;
import jp.hakamap.project.domain.value.AssetId;
import jp.hakamap.project.infrastructure.storage.StagedAsset;

public final class ProjectAssetStaging {
  private final Path root;

  private final Map<UUID, Map<AssetId, StagedAsset>> stagedByProject = new LinkedHashMap<>();

  public ProjectAssetStaging(Path root) {
    this.root = root.toAbsolutePath().normalize();
  }

  public synchronized Path conversionDirectory(UUID projectId) {
    return root.resolve(projectId.toString()).resolve("conversion").normalize();
  }

  public synchronized void cleanConversionDirectory(UUID projectId) {
    Path projectRoot = root.resolve(projectId.toString());
    try {
      Files.deleteIfExists(projectRoot.resolve("conversion"));
      Files.deleteIfExists(projectRoot);
    } catch (IOException ignored) {
      // 変換中ファイルが残る場合は次回起動時の一時アセット清掃へ回す。
    }
  }

  public synchronized StagedAsset stage(
      UUID projectId,
      AssetMetadata metadata,
      AssetIngestor.PreparedAsset prepared,
      AssetIngestor ingestor) {
    Path projectRoot = root.resolve(projectId.toString()).normalize();
    if (!projectRoot.startsWith(root)) {
      throw new EditingApiException("asset-integrity-invalid");
    }
    String extension = prepared.storedExtension();
    Path target = projectRoot.resolve(metadata.id().value() + "." + extension);
    try {
      ingestor.place(prepared, target);
      StagedAsset staged = new StagedAsset(target, metadata);
      stagedByProject
          .computeIfAbsent(projectId, ignored -> new LinkedHashMap<>())
          .put(metadata.id(), staged);
      return staged;
    } catch (RuntimeException exception) {
      deleteQuietly(target);
      throw exception;
    }
  }

  public synchronized List<StagedAsset> list(UUID projectId) {
    Map<AssetId, StagedAsset> staged = stagedByProject.get(projectId);
    return staged == null ? List.of() : List.copyOf(staged.values());
  }

  public synchronized Optional<StagedAsset> find(UUID projectId, AssetId assetId) {
    Map<AssetId, StagedAsset> staged = stagedByProject.get(projectId);
    return staged == null ? Optional.empty() : Optional.ofNullable(staged.get(assetId));
  }

  public synchronized List<StagedAssetV1> recoveryEntries(UUID projectId) {
    return list(projectId).stream()
        .map(
            staged ->
                new StagedAssetV1(
                    staged.metadata().id().value(),
                    root.relativize(staged.source().toAbsolutePath().normalize())
                        .toString()
                        .replace('\\', '/'),
                    staged.metadata().sizeBytes(),
                    staged.metadata().sha256()))
        .toList();
  }

  public synchronized void restore(
      UUID projectId, ProjectAggregate project, List<StagedAssetV1> recoveryEntries) {
    stagedByProject.remove(projectId);
    Map<AssetId, StagedAsset> restored = new LinkedHashMap<>();
    for (StagedAssetV1 entry : recoveryEntries) {
      AssetId assetId = new AssetId(entry.assetId());
      AssetMetadata metadata = project.assets().get(assetId);
      Path source = root.resolve(entry.tempRelativePath()).normalize();
      if (metadata == null
          || !source.startsWith(root)
          || Files.isSymbolicLink(source)
          || !Files.isRegularFile(source)
          || metadata.sizeBytes() != entry.sizeBytes()
          || !metadata.sha256().equals(entry.sha256())) {
        throw new EditingApiException("asset-integrity-invalid");
      }
      restored.put(assetId, new StagedAsset(source, metadata));
    }
    if (!restored.isEmpty()) {
      stagedByProject.put(projectId, restored);
    }
  }

  public synchronized void remove(UUID projectId, AssetId assetId) {
    Map<AssetId, StagedAsset> staged = stagedByProject.get(projectId);
    if (staged == null) {
      return;
    }
    StagedAsset removed = staged.remove(assetId);
    if (removed != null) {
      deleteQuietly(removed.source());
    }
    if (staged.isEmpty()) {
      stagedByProject.remove(projectId);
      deleteEmptyProjectDirectory(projectId);
    }
  }

  public synchronized void discard(UUID projectId) {
    Map<AssetId, StagedAsset> staged = stagedByProject.remove(projectId);
    if (staged != null) {
      staged.values().forEach(asset -> deleteQuietly(asset.source()));
    }
    deleteEmptyProjectDirectory(projectId);
  }

  public synchronized void forget(UUID projectId) {
    stagedByProject.remove(projectId);
    deleteEmptyProjectDirectory(projectId);
  }

  private void deleteEmptyProjectDirectory(UUID projectId) {
    cleanConversionDirectory(projectId);
  }

  private void deleteQuietly(Path path) {
    try {
      Files.deleteIfExists(path);
    } catch (IOException ignored) {
      // 残骸は次回起動時の一時アセット清掃へ回す。
    }
  }
}
