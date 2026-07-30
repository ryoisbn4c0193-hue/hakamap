package jp.hakamap.project.application.editing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BackgroundTileServiceTest {
  static {
    System.setProperty("java.awt.headless", "true");
  }

  @TempDir Path temporaryDirectory;

  @Test
  void createsAReusablePyramidOfTilesNoLargerThan1024Pixels() throws Exception {
    Path source = temporaryDirectory.resolve("background.png");
    BufferedImage image = new BufferedImage(2050, 1030, BufferedImage.TYPE_INT_RGB);
    ImageIO.write(image, "png", source.toFile());
    BackgroundTileService service =
        new BackgroundTileService(temporaryDirectory.resolve("background-tiles"));
    ProjectEditingApiService.AssetContent content =
        new ProjectEditingApiService.AssetContent(
            source,
            "image/png",
            java.nio.file.Files.size(source),
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");

    BackgroundTileService.TileManifest manifest = service.manifest(content);

    assertThat(manifest.width()).isEqualTo(2050);
    assertThat(manifest.height()).isEqualTo(1030);
    assertThat(manifest.tileSize()).isEqualTo(1024);
    assertThat(manifest.maximumLevel()).isEqualTo(2);
    assertThat(service.tile(content, 0, 2, 1).sizeBytes()).isPositive();
    assertThat(service.manifest(content)).isEqualTo(manifest);
  }

  @Test
  void createsTilesFromASupportedWebpBackground() throws Exception {
    Path source = temporaryDirectory.resolve("background.webp");
    BufferedImage image = new BufferedImage(48, 32, BufferedImage.TYPE_INT_RGB);
    assertThat(ImageIO.write(image, "webp", source.toFile())).isTrue();
    BackgroundTileService service =
        new BackgroundTileService(temporaryDirectory.resolve("background-tiles"));
    ProjectEditingApiService.AssetContent content =
        new ProjectEditingApiService.AssetContent(
            source,
            "image/webp",
            java.nio.file.Files.size(source),
            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");

    assertThatCode(() -> service.manifest(content)).doesNotThrowAnyException();
    assertThat(service.manifest(content).width()).isEqualTo(48);
  }
}
