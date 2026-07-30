package jp.hakamap.infrastructure.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ApplicationLauncherConfigurationTest {
  @Test
  void disablesSpringHeadlessModeForDesktopFileDialogs() throws Exception {
    Path source =
        Path.of("src/main/java/jp/hakamap/infrastructure/lifecycle/ApplicationLauncher.java")
            .toAbsolutePath()
            .normalize();

    assertThat(Files.readString(source))
        .contains("application.setHeadless(false)")
        .contains("new DesktopBrowserLauncher()");
  }
}
