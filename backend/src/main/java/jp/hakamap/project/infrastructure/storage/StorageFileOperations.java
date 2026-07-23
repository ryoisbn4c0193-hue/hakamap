package jp.hakamap.project.infrastructure.storage;

import java.nio.file.Path;
import java.util.List;

public interface StorageFileOperations {
  byte[] read(Path path);

  void writeAndForce(Path path, byte[] bytes);

  void copyAndForce(Path source, Path target);

  void atomicMoveReplacing(Path source, Path target);

  void atomicMoveNew(Path source, Path target);

  boolean exists(Path path);

  void deleteIfExists(Path path);

  long usableSpace(Path path);

  List<Path> list(Path directory);
}
