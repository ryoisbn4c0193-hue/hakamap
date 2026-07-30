package jp.hakamap.infrastructure.lifecycle;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.util.List;

public final class DesktopBrowserLauncher implements BrowserLauncher {
  @Override
  public void open(URI uri) {
    if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
      try {
        Desktop.getDesktop().browse(uri);
        return;
      } catch (IOException | UnsupportedOperationException ignored) {
        // Windowsの縮小ランタイムではDesktopが利用可能と判定されても失敗する場合がある。
      }
    }
    if (!isWindows()) {
      throw new LifecycleException("browser-launch-unavailable");
    }

    try {
      new ProcessBuilder(windowsCommand(uri)).start();
    } catch (IOException | SecurityException exception) {
      throw new LifecycleException("browser-launch-failed", exception);
    }
  }

  static List<String> windowsCommand(URI uri) {
    return List.of("rundll32.exe", "url.dll,FileProtocolHandler", uri.toASCIIString());
  }

  private boolean isWindows() {
    return System.getProperty("os.name", "").startsWith("Windows");
  }
}
