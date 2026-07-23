package jp.hakamap.persistence.json.validation;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import jp.hakamap.persistence.json.JsonPersistenceException;
import jp.hakamap.persistence.json.model.project.AssetV1;
import jp.hakamap.persistence.json.model.recovery.RecoveryFileV1;
import jp.hakamap.persistence.json.model.recovery.StagedAssetV1;

public final class RecoveryFileV1Validator {
  public void validate(RecoveryFileV1 recovery, Path temporaryAssetRoot) {
    if (recovery.recoverySchemaVersion() != 1
        || recovery.projectSnapshot() == null
        || !recovery.projectId().equals(recovery.projectSnapshot().project().id())
        || recovery.stagedAssets() == null) {
      fail();
    }
    Map<UUID, AssetV1> projectAssets = new HashMap<>();
    recovery.projectSnapshot().assets().forEach(asset -> projectAssets.put(asset.id(), asset));
    Set<UUID> stagedIds = new HashSet<>();
    for (StagedAssetV1 staged : recovery.stagedAssets()) {
      AssetV1 asset = projectAssets.get(staged.assetId());
      if (!stagedIds.add(staged.assetId())
          || asset == null
          || assetSize(asset) != staged.sizeBytes()
          || !assetSha256(asset).equals(staged.sha256())
          || !stagedFileIsValid(staged, asset, temporaryAssetRoot)) {
        fail();
      }
    }
  }

  private boolean stagedFileIsValid(StagedAssetV1 staged, AssetV1 asset, Path temporaryAssetRoot) {
    Path root = temporaryAssetRoot.toAbsolutePath().normalize();
    Path relative = Path.of(staged.tempRelativePath());
    if (relative.isAbsolute() || !fileNameMatches(staged, asset, relative)) {
      return false;
    }
    Path resolved = root.resolve(relative).normalize();
    if (!resolved.startsWith(root)
        || Files.isSymbolicLink(resolved)
        || !Files.isRegularFile(resolved, LinkOption.NOFOLLOW_LINKS)) {
      return false;
    }
    try {
      return Files.size(resolved) == staged.sizeBytes()
          && sha256(resolved).equals(staged.sha256())
          && signatureMatches(resolved, storedMediaType(asset));
    } catch (IOException exception) {
      throw new JsonPersistenceException("recovery-integrity-invalid", exception);
    }
  }

  private boolean fileNameMatches(StagedAssetV1 staged, AssetV1 asset, Path relative) {
    String fileName = relative.getFileName().toString();
    int extensionIndex = fileName.lastIndexOf('.');
    if (extensionIndex <= 0
        || !fileName.substring(0, extensionIndex).equals(staged.assetId().toString())) {
      return false;
    }
    String extension = fileName.substring(extensionIndex + 1);
    String mediaType = storedMediaType(asset);
    return switch (mediaType) {
      case "image/png" -> extension.equals("png");
      case "image/jpeg" -> extension.equals("jpg") || extension.equals("jpeg");
      case "image/webp" -> extension.equals("webp");
      default -> false;
    };
  }

  private String storedMediaType(AssetV1 asset) {
    if (asset instanceof jp.hakamap.persistence.json.model.project.BackgroundAssetV1 background) {
      return background.storedMediaType();
    }
    return ((jp.hakamap.persistence.json.model.project.AttachmentAssetV1) asset).storedMediaType();
  }

  private boolean signatureMatches(Path path, String mediaType) throws IOException {
    byte[] header = new byte[12];
    try (InputStream input = Files.newInputStream(path)) {
      if (input.read(header) < header.length) {
        return false;
      }
    }
    return switch (mediaType) {
      case "image/png" ->
          header[0] == (byte) 0x89
              && header[1] == 0x50
              && header[2] == 0x4e
              && header[3] == 0x47
              && header[4] == 0x0d
              && header[5] == 0x0a
              && header[6] == 0x1a
              && header[7] == 0x0a;
      case "image/jpeg" ->
          header[0] == (byte) 0xff && header[1] == (byte) 0xd8 && header[2] == (byte) 0xff;
      case "image/webp" ->
          new String(header, 0, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("RIFF")
              && new String(header, 8, 4, java.nio.charset.StandardCharsets.US_ASCII)
                  .equals("WEBP");
      default -> false;
    };
  }

  private String sha256(Path path) throws IOException {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      try (InputStream input = Files.newInputStream(path)) {
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) >= 0) {
          digest.update(buffer, 0, read);
        }
      }
      return HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256を利用できません。", exception);
    }
  }

  private long assetSize(AssetV1 asset) {
    if (asset instanceof jp.hakamap.persistence.json.model.project.BackgroundAssetV1 background) {
      return background.sizeBytes();
    }
    return ((jp.hakamap.persistence.json.model.project.AttachmentAssetV1) asset).sizeBytes();
  }

  private String assetSha256(AssetV1 asset) {
    if (asset instanceof jp.hakamap.persistence.json.model.project.BackgroundAssetV1 background) {
      return background.sha256();
    }
    return ((jp.hakamap.persistence.json.model.project.AttachmentAssetV1) asset).sha256();
  }

  private void fail() {
    throw new JsonPersistenceException("recovery-integrity-invalid");
  }
}
