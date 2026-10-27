package jp.hakamap.infrastructure.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WindowsAppBrowserTest {
  @TempDir Path directory;

  @Test
  void prefersEdgeAndUsesChromeOnlyWhenEdgeIsMissing() throws Exception {
    Path edge = directory.resolve("Microsoft/Edge/Application/msedge.exe");
    Path chrome = directory.resolve("Google/Chrome/Application/chrome.exe");
    Files.createDirectories(edge.getParent());
    Files.createDirectories(chrome.getParent());
    Files.createFile(chrome);
    Map<String, String> environment = Map.of("LOCALAPPDATA", directory.toString());
    assertThat(WindowsAppBrowser.findExecutable(environment)).contains(chrome);
    Files.createFile(edge);
    assertThat(WindowsAppBrowser.findExecutable(environment)).contains(edge);
    assertThat(WindowsAppBrowser.findExecutable(Map.of())).isEmpty();
  }

  @Test
  void passesPathAndBootstrapAsSeparateArgumentsAndRejectsExternalUrls() {
    Path browser = directory.resolve("Program Files/Edge/msedge.exe");
    Path profile = directory.resolve("reference profile");
    assertThat(
            WindowsAppBrowser.command(
                browser, profile, URI.create("http://127.0.0.1:1234/#bootstrap=token")))
        .containsExactly(
            browser.toString(),
            "--user-data-dir=" + profile,
            "--no-first-run",
            "--no-default-browser-check",
            "--disable-background-mode",
            "--app=http://127.0.0.1:1234/#bootstrap=token");
    assertThatThrownBy(
            () -> WindowsAppBrowser.command(browser, profile, URI.create("https://example.com/")))
        .isInstanceOf(LifecycleException.class);
  }

  @Test
  void reopensExistingWindowWithoutReloadOrStartingAnotherBrowser() throws Exception {
    WindowsAppBrowser.Windows windows = mock(WindowsAppBrowser.Windows.class);
    WindowsAppBrowser.Starter starter = mock(WindowsAppBrowser.Starter.class);
    Process process = mock(Process.class);
    when(process.pid()).thenReturn(123L);
    when(process.descendants()).thenAnswer(call -> Stream.empty());
    when(starter.start(any())).thenReturn(process);
    when(windows.handles()).thenReturn(Set.of(7L));
    when(windows.find(any(), any())).thenReturn(Optional.of(42L));
    when(windows.isWindow(42L)).thenReturn(true);
    when(windows.activate(42L)).thenReturn(true);
    Path executable = directory.resolve("Hakamap.exe");
    Path icon = directory.resolve("hakamap.ico");
    WindowsAppBrowser browser =
        new WindowsAppBrowser(
            directory.resolve("msedge.exe"),
            directory.resolve("profile"),
            executable,
            icon,
            windows,
            starter);
    browser.open(URI.create("http://127.0.0.1:1234/#bootstrap=first"));
    browser.open(URI.create("http://127.0.0.1:1234/#bootstrap=second"));
    verify(starter, times(1)).start(any());
    verify(windows).identify(42L, executable, icon);
    verify(windows, times(2)).activate(42L);
    verify(windows).find(Set.of(123L), Set.of(7L));
    when(windows.activate(42L)).thenReturn(false);
    assertThatThrownBy(() -> browser.open(URI.create("http://127.0.0.1:1234/#bootstrap=blocked")))
        .isInstanceOf(LifecycleException.class);
    verify(starter, times(1)).start(any());
    when(windows.activate(42L)).thenReturn(true);
    when(windows.isWindow(42L)).thenReturn(false);
    browser.open(URI.create("http://127.0.0.1:1234/#bootstrap=third"));
    verify(starter, times(2)).start(any());
    when(windows.isWindow(42L)).thenReturn(true);
    browser.close();
    verify(windows).close(42L);
  }
}
