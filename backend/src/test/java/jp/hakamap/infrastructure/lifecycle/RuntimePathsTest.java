package jp.hakamap.infrastructure.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RuntimePathsTest {
  @Test
  void separatesRuntimeDataFromWindowsInstallationDirectory() {
    RuntimePaths paths =
        RuntimePaths.forCurrentUser(Map.of("LOCALAPPDATA", "C:\\Users\\test\\AppData\\Local"));

    assertThat(paths.directory())
        .isEqualTo(Path.of("C:\\Users\\test\\AppData\\Local", "HakamapData", "runtime"));
    assertThat(paths.directory().toString()).doesNotContain("Hakamap\\runtime");
  }
}
