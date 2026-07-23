package jp.hakamap.persistence.json.repository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import jp.hakamap.persistence.json.JsonPersistenceException;

final class RepositoryFiles {
  private RepositoryFiles() {}

  static byte[] read(Path file) {
    try {
      return Files.readAllBytes(file);
    } catch (IOException exception) {
      throw new JsonPersistenceException("json-read-failed", exception);
    }
  }

  static void write(Path file, byte[] bytes) {
    try {
      Path parent = file.getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
      Files.write(file, bytes);
    } catch (IOException exception) {
      throw new JsonPersistenceException("json-write-failed", exception);
    }
  }
}
