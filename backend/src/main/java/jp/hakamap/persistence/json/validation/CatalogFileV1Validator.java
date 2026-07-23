package jp.hakamap.persistence.json.validation;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import jp.hakamap.persistence.json.JsonPersistenceException;
import jp.hakamap.persistence.json.model.catalog.ActiveCatalogProjectV1;
import jp.hakamap.persistence.json.model.catalog.CatalogFileV1;
import jp.hakamap.persistence.json.model.catalog.CatalogProjectV1;

public final class CatalogFileV1Validator {
  public void validate(CatalogFileV1 catalog) {
    if (catalog.schemaVersion() != 1 || catalog.projects() == null) {
      fail();
    }
    Set<UUID> ids = new HashSet<>();
    Set<String> paths = new HashSet<>();
    for (CatalogProjectV1 project : catalog.projects()) {
      if (!ids.add(project.projectId()) || !paths.add(normalizeWindowsPath(project.path()))) {
        fail();
      }
      if (project.lastKnownUpdatedAt().isBefore(project.lastKnownCreatedAt())) {
        fail();
      }
    }
    if (catalog.defaultProjectId() != null) {
      boolean activeDefault =
          catalog.projects().stream()
              .filter(ActiveCatalogProjectV1.class::isInstance)
              .anyMatch(project -> project.projectId().equals(catalog.defaultProjectId()));
      if (!activeDefault) {
        fail();
      }
    }
  }

  private String normalizeWindowsPath(String path) {
    if (path == null) {
      fail();
    }
    String windowsPath = path.replace('/', '\\');
    boolean drivePath = windowsPath.matches("^[A-Za-z]:\\\\.*");
    boolean uncPath = windowsPath.matches("^\\\\\\\\[^\\\\]+\\\\[^\\\\]+(?:\\\\.*)?$");
    if (!drivePath && !uncPath) {
      fail();
    }
    String[] parts = windowsPath.split("\\\\");
    java.util.ArrayDeque<String> normalized = new java.util.ArrayDeque<>();
    int rootParts = drivePath ? 1 : 2;
    for (String part : parts) {
      if (part.isEmpty() || part.equals(".")) {
        continue;
      }
      if (part.equals("..")) {
        if (normalized.size() <= rootParts) {
          fail();
        }
        normalized.removeLast();
      } else {
        normalized.addLast(part);
      }
    }
    String prefix = uncPath ? "\\\\" : "";
    return (prefix + String.join("\\", normalized)).toLowerCase(Locale.ROOT);
  }

  private void fail() {
    throw new JsonPersistenceException("catalog-integrity-invalid");
  }
}
