package jp.hakamap.persistence.json.validation;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import jp.hakamap.persistence.json.JsonPersistenceException;
import jp.hakamap.project.domain.model.AssetMetadata;
import jp.hakamap.project.domain.model.ProjectAggregate;
import jp.hakamap.project.domain.value.AssetType;

public final class ProjectAssetFileValidator {
  public void validate(Path projectRoot, ProjectAggregate project) {
    Path normalizedRoot = projectRoot.toAbsolutePath().normalize();
    for (AssetMetadata asset : project.assets().values()) {
      Path relative = Path.of(asset.relativePath());
      String expectedPrefix =
          asset.type() == AssetType.BACKGROUND ? "assets/backgrounds/" : "assets/attachments/";
      if (relative.isAbsolute()
          || !asset.relativePath().startsWith(expectedPrefix)
          || !fileNameMatchesAsset(asset, relative)) {
        fail();
      }
      Path resolved = normalizedRoot.resolve(relative).normalize();
      if (!resolved.startsWith(normalizedRoot)
          || Files.isSymbolicLink(resolved)
          || !Files.isRegularFile(resolved, LinkOption.NOFOLLOW_LINKS)) {
        fail();
      }
      try {
        if (Files.size(resolved) != asset.sizeBytes()
            || !sha256(resolved).equals(asset.sha256())
            || !signatureMatches(resolved, asset.storedMediaType())) {
          fail();
        }
      } catch (IOException exception) {
        throw new JsonPersistenceException("asset-integrity-invalid", exception);
      }
    }
  }

  private boolean fileNameMatchesAsset(AssetMetadata asset, Path relative) {
    String fileName = relative.getFileName().toString();
    int extensionIndex = fileName.lastIndexOf('.');
    if (extensionIndex <= 0
        || !fileName.substring(0, extensionIndex).equals(asset.id().value().toString())) {
      return false;
    }
    String extension = fileName.substring(extensionIndex + 1);
    return switch (asset.storedMediaType()) {
      case "image/png" -> extension.equals("png");
      case "image/jpeg" -> extension.equals("jpg") || extension.equals("jpeg");
      case "image/webp" -> extension.equals("webp");
      default -> false;
    };
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

  private void fail() {
    throw new JsonPersistenceException("asset-integrity-invalid");
  }
}
