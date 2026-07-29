package jp.hakamap.infrastructure.lifecycle;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;

public final class DesktopBrowserLauncher implements BrowserLauncher {
  @Override
  public void open(URI uri) {
    if (!Desktop.isDesktopSupported()) {
      throw new LifecycleException("browser-launch-unavailable");
    }
    try {
      Desktop.getDesktop().browse(uri);
    } catch (IOException | UnsupportedOperationException exception) {
      throw new LifecycleException("browser-launch-failed", exception);
    }
  }
}
