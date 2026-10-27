package jp.hakamap.infrastructure.lifecycle;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class DesktopBrowserLauncher implements BrowserLauncher {
  private BrowserLauncher appBrowser;

  @Override
  public synchronized void open(URI uri) {
    if (isWindows() && System.getProperty("jpackage.app-path") != null) {
      try {
        openAppWindow(uri);
      } catch (LifecycleException exception) {
        String message =
            exception.code().equals("app-browser-not-installed")
                ? "専用画面を開くにはMicrosoft EdgeまたはGoogle Chromeが必要です。インストールしてから起動してください。"
                : "Hakamapの専用画面を開けませんでした。Hakamapを終了して開き直してください。";
        javax.swing.JOptionPane.showMessageDialog(
            null, message, "Hakamap", javax.swing.JOptionPane.ERROR_MESSAGE);
        throw exception;
      }
      return;
    }
    openDefaultBrowser(uri);
  }

  private void openAppWindow(URI uri) {
    if (appBrowser == null) {
      Path launcher = Path.of(System.getProperty("jpackage.app-path")).toAbsolutePath();
      Path icon = launcher.getParent().resolve("app").resolve("hakamap.ico");
      Path profile =
          RuntimePaths.forCurrentUser(System.getenv())
              .directory()
              .getParent()
              .resolve("browser-profile");
      Path executable =
          WindowsAppBrowser.findExecutable(System.getenv())
              .orElseThrow(() -> new LifecycleException("app-browser-not-installed"));
      if (!Files.isRegularFile(icon)) {
        throw new LifecycleException("app-window-icon-missing");
      }
      WindowsShortcutIdentity.repair(launcher);
      appBrowser =
          new WindowsAppBrowser(
              executable,
              profile,
              launcher,
              icon,
              new WindowsAppIdentity(),
              command -> new ProcessBuilder(command).start());
    }
    appBrowser.open(uri);
  }

  private void openDefaultBrowser(URI uri) {
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

  @Override
  public synchronized void close() {
    if (appBrowser != null) {
      appBrowser.close();
    }
  }

  static List<String> windowsCommand(URI uri) {
    return List.of("rundll32.exe", "url.dll,FileProtocolHandler", uri.toASCIIString());
  }

  private boolean isWindows() {
    return System.getProperty("os.name", "").startsWith("Windows");
  }
}
