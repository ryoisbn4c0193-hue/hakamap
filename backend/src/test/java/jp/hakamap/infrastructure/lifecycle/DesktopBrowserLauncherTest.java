package jp.hakamap.infrastructure.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.junit.jupiter.api.Test;

class DesktopBrowserLauncherTest {
  @Test
  void buildsWindowsUrlHandlerCommandWithSeparatedArguments() {
    URI uri = URI.create("http://127.0.0.1:54321/#bootstrap=token");

    assertThat(DesktopBrowserLauncher.windowsCommand(uri))
        .containsExactly(
            "rundll32.exe",
            "url.dll,FileProtocolHandler",
            "http://127.0.0.1:54321/#bootstrap=token");
  }
}
