package jp.hakamap.project.application.editing;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

/** 背景原本から、再生成可能な表示用タイル階層を作成する。 */
public final class BackgroundTileService {
  static final int TILE_SIZE = 1024;

  private final Path cacheRoot;

  public BackgroundTileService(Path cacheRoot) {
    this.cacheRoot = cacheRoot.toAbsolutePath().normalize();
  }

  public synchronized TileManifest manifest(ProjectEditingApiService.AssetContent content) {
    Path directory = ensureTiles(content);
    try {
      List<String> values = Files.readAllLines(directory.resolve("manifest"));
      return new TileManifest(
          Integer.parseInt(values.get(0)),
          Integer.parseInt(values.get(1)),
          TILE_SIZE,
          Integer.parseInt(values.get(2)));
    } catch (IOException | RuntimeException exception) {
      throw new EditingApiException("background-tile-invalid");
    }
  }

  public synchronized TileContent tile(
      ProjectEditingApiService.AssetContent content, int level, int column, int row) {
    TileManifest manifest = manifest(content);
    if (level < 0
        || level > manifest.maximumLevel()
        || column < 0
        || row < 0
        || column >= columns(manifest.width(), level)
        || row >= columns(manifest.height(), level)) {
      throw new EditingApiException("background-tile-not-found");
    }
    Path directory = cacheDirectory(content.sha256());
    Path tile = directory.resolve(level + "-" + column + "-" + row + ".png").normalize();
    if (!tile.startsWith(directory) || Files.isSymbolicLink(tile) || !Files.isRegularFile(tile)) {
      throw new EditingApiException("background-tile-not-found");
    }
    try {
      return new TileContent(tile, Files.size(tile));
    } catch (IOException exception) {
      throw new EditingApiException("background-tile-not-found");
    }
  }

  private Path ensureTiles(ProjectEditingApiService.AssetContent content) {
    Path directory = cacheDirectory(content.sha256());
    Path manifest = directory.resolve("manifest");
    if (Files.isRegularFile(manifest) && !Files.isSymbolicLink(manifest)) {
      return directory;
    }
    try {
      BufferedImage original = ImageIO.read(content.path().toFile());
      if (original == null) {
        throw new EditingApiException("background-tile-invalid");
      }
      Files.createDirectories(directory);
      int level = 0;
      BufferedImage image = original;
      while (true) {
        writeLevel(directory, level, image);
        if (image.getWidth() <= TILE_SIZE && image.getHeight() <= TILE_SIZE) {
          break;
        }
        image = scaleHalf(image);
        level++;
      }
      List<String> values =
          new ArrayList<>(
              List.of(String.valueOf(original.getWidth()), String.valueOf(original.getHeight())));
      values.add(String.valueOf(level));
      Files.write(
          manifest,
          values,
          StandardOpenOption.CREATE,
          StandardOpenOption.TRUNCATE_EXISTING,
          StandardOpenOption.WRITE);
      return directory;
    } catch (IOException exception) {
      throw new EditingApiException("background-tile-invalid");
    }
  }

  private void writeLevel(Path directory, int level, BufferedImage image) throws IOException {
    int columns = columns(image.getWidth(), 0);
    int rows = columns(image.getHeight(), 0);
    for (int row = 0; row < rows; row++) {
      for (int column = 0; column < columns; column++) {
        int x = column * TILE_SIZE;
        int y = row * TILE_SIZE;
        int width = Math.min(TILE_SIZE, image.getWidth() - x);
        int height = Math.min(TILE_SIZE, image.getHeight() - y);
        BufferedImage tile = image.getSubimage(x, y, width, height);
        Path tilePath = directory.resolve(level + "-" + column + "-" + row + ".png");
        if (!ImageIO.write(tile, "png", tilePath.toFile())) {
          throw new IOException("PNG writer is unavailable");
        }
      }
    }
  }

  private BufferedImage scaleHalf(BufferedImage source) {
    int width = Math.max(1, (source.getWidth() + 1) / 2);
    int height = Math.max(1, (source.getHeight() + 1) / 2);
    BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    Graphics2D graphics = scaled.createGraphics();
    try {
      graphics.setRenderingHint(
          RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
      graphics.drawImage(source, 0, 0, width, height, null);
    } finally {
      graphics.dispose();
    }
    return scaled;
  }

  private Path cacheDirectory(String sha256) {
    if (!sha256.matches("^[0-9a-f]{64}$")) {
      throw new EditingApiException("background-tile-invalid");
    }
    return cacheRoot.resolve(sha256).normalize();
  }

  private static int columns(int length, int level) {
    int scaled = Math.max(1, (int) Math.ceil(length / Math.pow(2, level)));
    return (scaled + TILE_SIZE - 1) / TILE_SIZE;
  }

  public record TileManifest(int width, int height, int tileSize, int maximumLevel) {}

  public record TileContent(Path path, long sizeBytes) {}
}
