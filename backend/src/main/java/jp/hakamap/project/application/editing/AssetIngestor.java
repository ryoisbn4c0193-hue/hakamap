package jp.hakamap.project.application.editing;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

final class AssetIngestor {
  private static final long ATTACHMENT_MAXIMUM_BYTES = 25L * 1024 * 1024;

  private static final long BACKGROUND_MAXIMUM_BYTES = 100L * 1024 * 1024;

  private static final long ATTACHMENT_MAXIMUM_PIXELS = 40_000_000L;

  private static final long BACKGROUND_MAXIMUM_PIXELS = 100_000_000L;

  private static final int ATTACHMENT_MAXIMUM_EDGE = 15_000;

  private static final int BACKGROUND_MAXIMUM_EDGE = 30_000;

  PreparedAsset prepare(Path source, boolean background, Path conversionDirectory) {
    try {
      long maximumBytes = background ? BACKGROUND_MAXIMUM_BYTES : ATTACHMENT_MAXIMUM_BYTES;
      if (!Files.isRegularFile(source)
          || Files.isSymbolicLink(source)
          || Files.size(source) <= 0
          || Files.size(source) > maximumBytes) {
        throw new EditingApiException("asset-size-exceeded");
      }
      String fileName = source.getFileName().toString();
      String extension = extension(fileName);
      if (extension.equals("pdf")) {
        return preparePdf(source, fileName, background, conversionDirectory);
      }
      byte[] header = readHeader(source, 32);
      ImageKind kind = imageKind(header, extension);
      Dimensions dimensions =
          kind == ImageKind.WEBP ? webpDimensions(header) : imageDimensions(source);
      requireDimensions(dimensions, background);
      return new PreparedAsset(
          source,
          fileName,
          kind.mediaType,
          kind.mediaType,
          kind.extension,
          Files.size(source),
          sha256(source),
          false);
    } catch (EditingApiException exception) {
      throw exception;
    } catch (IOException exception) {
      throw new EditingApiException("asset-integrity-invalid");
    }
  }

  void place(PreparedAsset asset, Path target) {
    try {
      Files.createDirectories(target.getParent());
      Files.copy(asset.preparedPath(), target, StandardCopyOption.COPY_ATTRIBUTES);
      if (Files.size(target) != asset.sizeBytes() || !sha256(target).equals(asset.sha256())) {
        Files.deleteIfExists(target);
        throw new EditingApiException("asset-integrity-invalid");
      }
    } catch (IOException exception) {
      throw new EditingApiException("asset-integrity-invalid");
    }
  }

  private PreparedAsset preparePdf(
      Path source, String fileName, boolean background, Path conversionDirectory)
      throws IOException {
    Path converted = null;
    try (PDDocument document = Loader.loadPDF(source.toFile())) {
      if (document.isEncrypted() || document.getNumberOfPages() != 1) {
        throw new EditingApiException("asset-format-unsupported");
      }
      var pageSize = document.getPage(0).getCropBox();
      requireDimensions(
          new Dimensions(
              Math.max(1, Math.round(pageSize.getWidth() * 150 / 72)),
              Math.max(1, Math.round(pageSize.getHeight() * 150 / 72))),
          background);
      BufferedImage image = new PDFRenderer(document).renderImageWithDPI(0, 150);
      requireDimensions(new Dimensions(image.getWidth(), image.getHeight()), background);
      Files.createDirectories(conversionDirectory);
      converted = Files.createTempFile(conversionDirectory, ".pdf-converted-", ".png");
      if (!ImageIO.write(image, "png", converted.toFile())) {
        throw new EditingApiException("asset-integrity-invalid");
      }
      return new PreparedAsset(
          converted,
          fileName,
          "application/pdf",
          "image/png",
          "png",
          Files.size(converted),
          sha256(converted),
          true);
    } catch (EditingApiException exception) {
      if (converted != null) {
        Files.deleteIfExists(converted);
      }
      throw exception;
    } catch (RuntimeException exception) {
      if (converted != null) {
        Files.deleteIfExists(converted);
      }
      throw new EditingApiException("asset-format-unsupported");
    }
  }

  private ImageKind imageKind(byte[] header, String extension) {
    if (startsWith(header, new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a})) {
      requireExtension(extension, "png");
      return ImageKind.PNG;
    }
    if (header.length >= 3
        && header[0] == (byte) 0xff
        && header[1] == (byte) 0xd8
        && header[2] == (byte) 0xff) {
      if (!extension.equals("jpg") && !extension.equals("jpeg")) {
        throw new EditingApiException("asset-format-unsupported");
      }
      return ImageKind.JPEG;
    }
    if (ascii(header, 0, 4).equals("RIFF") && ascii(header, 8, 4).equals("WEBP")) {
      requireExtension(extension, "webp");
      return ImageKind.WEBP;
    }
    throw new EditingApiException("asset-format-unsupported");
  }

  private Dimensions imageDimensions(Path path) throws IOException {
    try (ImageInputStream input = ImageIO.createImageInputStream(path.toFile())) {
      if (input == null) {
        throw new EditingApiException("asset-format-unsupported");
      }
      Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
      if (!readers.hasNext()) {
        throw new EditingApiException("asset-format-unsupported");
      }
      ImageReader reader = readers.next();
      try {
        reader.setInput(input, true, true);
        return new Dimensions(reader.getWidth(0), reader.getHeight(0));
      } finally {
        reader.dispose();
      }
    }
  }

  private Dimensions webpDimensions(byte[] header) {
    if (header.length < 30) {
      throw new EditingApiException("asset-format-unsupported");
    }
    String chunk = ascii(header, 12, 4);
    return switch (chunk) {
      case "VP8X" -> new Dimensions(1 + littleEndian24(header, 24), 1 + littleEndian24(header, 27));
      case "VP8L" -> {
        if ((header[20] & 0xff) != 0x2f) {
          throw new EditingApiException("asset-format-unsupported");
        }
        int width = 1 + (header[21] & 0xff) + ((header[22] & 0x3f) << 8);
        int height =
            1
                + ((header[22] & 0xc0) >> 6)
                + ((header[23] & 0xff) << 2)
                + ((header[24] & 0x0f) << 10);
        yield new Dimensions(width, height);
      }
      case "VP8 " -> {
        if (header.length < 30
            || header[23] != (byte) 0x9d
            || header[24] != 0x01
            || header[25] != 0x2a) {
          throw new EditingApiException("asset-format-unsupported");
        }
        int width = littleEndian16(header, 26) & 0x3fff;
        int height = littleEndian16(header, 28) & 0x3fff;
        yield new Dimensions(width, height);
      }
      default -> throw new EditingApiException("asset-format-unsupported");
    };
  }

  private void requireDimensions(Dimensions dimensions, boolean background) {
    int maximumEdge = background ? BACKGROUND_MAXIMUM_EDGE : ATTACHMENT_MAXIMUM_EDGE;
    long maximumPixels = background ? BACKGROUND_MAXIMUM_PIXELS : ATTACHMENT_MAXIMUM_PIXELS;
    if (dimensions.width() <= 0
        || dimensions.height() <= 0
        || dimensions.width() > maximumEdge
        || dimensions.height() > maximumEdge
        || Math.multiplyExact((long) dimensions.width(), dimensions.height()) > maximumPixels) {
      throw new EditingApiException("asset-dimensions-exceeded");
    }
  }

  private byte[] readHeader(Path path, int maximum) throws IOException {
    try (var input = Files.newInputStream(path)) {
      return input.readNBytes(maximum);
    }
  }

  private String sha256(Path path) {
    try (var input = Files.newInputStream(path)) {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] buffer = new byte[8192];
      int read;
      while ((read = input.read(buffer)) >= 0) {
        digest.update(buffer, 0, read);
      }
      return HexFormat.of().formatHex(digest.digest());
    } catch (IOException | NoSuchAlgorithmException exception) {
      throw new EditingApiException("asset-integrity-invalid");
    }
  }

  private String extension(String fileName) {
    int index = fileName.lastIndexOf('.');
    if (index <= 0 || index == fileName.length() - 1) {
      throw new EditingApiException("asset-format-unsupported");
    }
    return fileName.substring(index + 1).toLowerCase(java.util.Locale.ROOT);
  }

  private void requireExtension(String actual, String expected) {
    if (!actual.equals(expected)) {
      throw new EditingApiException("asset-format-unsupported");
    }
  }

  private boolean startsWith(byte[] value, byte[] prefix) {
    if (value.length < prefix.length) {
      return false;
    }
    for (int index = 0; index < prefix.length; index++) {
      if (value[index] != prefix[index]) {
        return false;
      }
    }
    return true;
  }

  private String ascii(byte[] bytes, int offset, int length) {
    if (offset < 0 || length < 0 || offset + length > bytes.length) {
      return "";
    }
    return new String(bytes, offset, length, java.nio.charset.StandardCharsets.US_ASCII);
  }

  private int littleEndian16(byte[] bytes, int offset) {
    return (bytes[offset] & 0xff) | ((bytes[offset + 1] & 0xff) << 8);
  }

  private int littleEndian24(byte[] bytes, int offset) {
    return (bytes[offset] & 0xff)
        | ((bytes[offset + 1] & 0xff) << 8)
        | ((bytes[offset + 2] & 0xff) << 16);
  }

  record PreparedAsset(
      Path preparedPath,
      String originalFileName,
      String sourceMediaType,
      String storedMediaType,
      String storedExtension,
      long sizeBytes,
      String sha256,
      boolean temporary) {}

  private record Dimensions(int width, int height) {}

  private enum ImageKind {
    PNG("image/png", "png"),
    JPEG("image/jpeg", "jpg"),
    WEBP("image/webp", "webp");

    private final String mediaType;

    private final String extension;

    ImageKind(String mediaType, String extension) {
      this.mediaType = mediaType;
      this.extension = extension;
    }
  }
}
