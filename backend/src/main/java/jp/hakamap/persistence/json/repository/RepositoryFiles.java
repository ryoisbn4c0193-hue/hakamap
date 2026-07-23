package jp.hakamap.persistence.json.repository;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
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
    Path temporary =
        file.resolveSibling("." + file.getFileName() + "-" + UUID.randomUUID() + ".tmp");
    try {
      Path parent = file.getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
      try (FileChannel channel =
          FileChannel.open(temporary, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        while (buffer.hasRemaining()) {
          channel.write(buffer);
        }
        channel.force(true);
      }
      Files.move(
          temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException exception) {
      throw new JsonPersistenceException("json-write-failed", exception);
    } finally {
      try {
        Files.deleteIfExists(temporary);
      } catch (IOException ignored) {
        // 既知の一時ファイルとして次回起動時の清掃対象にする。
      }
    }
  }
}
