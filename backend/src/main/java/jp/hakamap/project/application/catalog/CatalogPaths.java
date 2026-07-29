package jp.hakamap.project.application.catalog;

import java.nio.file.Path;
import java.util.Map;

public record CatalogPaths(Path catalogFile) {
  public static CatalogPaths forCurrentUser(Map<String, String> environment) {
    String localAppData = environment.get("LOCALAPPDATA");
    Path base =
        localAppData == null || localAppData.isBlank()
            ? Path.of(System.getProperty("user.home"), ".local", "share")
            : Path.of(localAppData);
    return new CatalogPaths(base.resolve("Hakamap").resolve("catalog.json"));
  }
}
