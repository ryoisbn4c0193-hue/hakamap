package jp.hakamap.project.application.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CatalogPathsTest {
  @Test
  void separatesCatalogFromWindowsInstallationDirectory() {
    CatalogPaths paths =
        CatalogPaths.forCurrentUser(Map.of("LOCALAPPDATA", "C:\\Users\\test\\AppData\\Local"));

    assertThat(paths.catalogFile())
        .isEqualTo(Path.of("C:\\Users\\test\\AppData\\Local", "HakamapData", "catalog.json"));
    assertThat(paths.catalogFile().toString()).doesNotContain("Hakamap\\catalog.json");
  }
}
