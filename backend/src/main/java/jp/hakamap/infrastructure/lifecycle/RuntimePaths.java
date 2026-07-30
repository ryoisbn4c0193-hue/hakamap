package jp.hakamap.infrastructure.lifecycle;

import java.nio.file.Path;
import java.util.Map;

public record RuntimePaths(Path directory) {
  public static RuntimePaths forCurrentUser(Map<String, String> environment) {
    String localAppData = environment.get("LOCALAPPDATA");
    Path base;
    if (localAppData != null && !localAppData.isBlank()) {
      base = Path.of(localAppData);
    } else {
      base = Path.of(System.getProperty("user.home"), ".local", "share");
    }
    return new RuntimePaths(base.resolve("HakamapData").resolve("runtime"));
  }

  public Path applicationLock() {
    return directory.resolve("application.lock");
  }

  public Path instanceFile() {
    return directory.resolve("instance.json");
  }

  public Path uncleanExitMarker() {
    return directory.resolve("unclean-exit.marker");
  }
}
