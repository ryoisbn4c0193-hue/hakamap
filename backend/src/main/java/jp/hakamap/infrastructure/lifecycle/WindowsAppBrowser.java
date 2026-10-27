package jp.hakamap.infrastructure.lifecycle;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** 専用プロファイルで開いたブラウザの画面だけを管理する。 */
final class WindowsAppBrowser implements BrowserLauncher {

  private final Path executable;

  private final Path profile;

  private final Path launcher;

  private final Path icon;

  private final Windows windows;

  private final Starter starter;

  private long window;

  private boolean identified;

  interface Windows {
    boolean isWindow(long window);

    Set<Long> handles();

    Optional<Long> find(Set<Long> processIds, Set<Long> excluded);

    void identify(long window, Path launcher, Path icon);

    boolean activate(long window);

    void maximize(long window);

    void close(long window);

    Set<Long> ownedProcesses();
  }

  @FunctionalInterface
  interface Starter {
    Process start(List<String> command) throws IOException;
  }

  WindowsAppBrowser(
      Path executable, Path profile, Path launcher, Path icon, Windows windows, Starter starter) {
    this.executable = executable;
    this.profile = profile;
    this.launcher = launcher;
    this.icon = icon;
    this.windows = windows;
    this.starter = starter;
  }

  static Optional<Path> findExecutable(Map<String, String> environment) {
    // Edgeを優先し、見つからない場合だけChromeを使用する。PATH上の任意プログラムは起動しない。
    for (String product : List.of("Microsoft/Edge", "Google/Chrome")) {
      for (String root : List.of("ProgramFiles(x86)", "ProgramFiles", "LOCALAPPDATA")) {
        String directory = environment.get(root);
        if (directory == null || directory.isBlank()) {
          continue;
        }
        String name = product.startsWith("Microsoft") ? "msedge.exe" : "chrome.exe";
        Path candidate = Path.of(directory).resolve(product).resolve("Application").resolve(name);
        if (Files.isRegularFile(candidate)) {
          return Optional.of(candidate);
        }
      }
    }
    return Optional.empty();
  }

  static List<String> command(Path executable, Path profile, URI uri) {
    if (!"http".equals(uri.getScheme())
        || !"127.0.0.1".equals(uri.getHost())
        || uri.getPort() <= 0
        || !"/".equals(uri.getPath())
        || uri.getUserInfo() != null
        || uri.getQuery() != null) {
      throw new LifecycleException("app-window-invalid-url");
    }
    return List.of(
        executable.toString(),
        "--user-data-dir=" + profile,
        "--no-first-run",
        "--no-default-browser-check",
        "--disable-background-mode",
        "--start-maximized",
        "--app=" + uri.toASCIIString());
  }

  @Override
  public synchronized void open(URI uri) {
    // 画面を再読込しないので、編集途中の内容・選択・検索条件も維持する。
    if (window != 0 && windows.isWindow(window)) {
      identifyWindow();
      if (!windows.activate(window)) {
        throw new LifecycleException("app-window-activation-failed");
      }
      return;
    }
    identified = false;
    Set<Long> before = windows.handles();
    Process process;
    try {
      Files.createDirectories(profile);
      process = starter.start(command(executable, profile, uri));
    } catch (IOException exception) {
      throw new LifecycleException("app-window-launch-failed");
    }
    Set<Long> processIds = new HashSet<>();
    processIds.add(process.pid());
    processIds.addAll(windows.ownedProcesses());
    // 専用プロファイルの残存プロセスへ起動要求が渡される場合も識別する。
    ProcessHandle.allProcesses()
        .forEach(
            candidate -> {
              String[] arguments = candidate.info().arguments().orElse(new String[0]);
              if (java.util.Arrays.asList(arguments).contains("--user-data-dir=" + profile)) {
                processIds.add(candidate.pid());
              }
            });
    long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
    while (System.nanoTime() < deadline) {
      process.descendants().forEach(child -> processIds.add(child.pid()));
      Optional<Long> found = windows.find(processIds, before);
      if (found.isPresent()) {
        window = found.orElseThrow();
        identifyWindow();
        windows.maximize(window);
        if (!windows.activate(window)) {
          throw new LifecycleException("app-window-activation-failed");
        }
        return;
      }
      try {
        Thread.sleep(100);
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        throw new LifecycleException("app-window-launch-interrupted", exception);
      }
    }
    throw new LifecycleException("app-window-not-found");
  }

  @Override
  public synchronized void close() {
    if (window != 0 && windows.isWindow(window)) {
      windows.close(window);
    }
  }

  private void identifyWindow() {
    if (!identified) {
      windows.identify(window, launcher, icon);
      identified = true;
    }
  }
}
