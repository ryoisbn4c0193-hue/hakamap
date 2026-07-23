package jp.hakamap.project.infrastructure.storage;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.List;

public final class NioStorageFileOperations implements StorageFileOperations {
  @Override
  public byte[] read(Path path) {
    try {
      if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
        throw new StorageException("storage-file-invalid");
      }
      return Files.readAllBytes(path);
    } catch (IOException exception) {
      throw new StorageException("storage-read-failed", exception);
    }
  }

  @Override
  public void writeAndForce(Path path, byte[] bytes) {
    try {
      createParent(path);
      try (FileChannel channel =
          FileChannel.open(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        while (buffer.hasRemaining()) {
          channel.write(buffer);
        }
        channel.force(true);
      }
    } catch (IOException exception) {
      throw new StorageException("storage-write-failed", exception);
    }
  }

  @Override
  public void copyAndForce(Path source, Path target) {
    try {
      if (Files.isSymbolicLink(source) || !Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) {
        throw new StorageException("staged-asset-invalid");
      }
      createParent(target);
      try (FileChannel input = FileChannel.open(source, StandardOpenOption.READ);
          FileChannel output =
              FileChannel.open(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
        long position = 0;
        while (position < input.size()) {
          position += input.transferTo(position, input.size() - position, output);
        }
        output.force(true);
      }
    } catch (IOException exception) {
      throw new StorageException("storage-copy-failed", exception);
    }
  }

  @Override
  public void atomicMoveReplacing(Path source, Path target) {
    try {
      Files.move(
          source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException exception) {
      throw new StorageException("storage-atomic-move-failed", exception);
    }
  }

  @Override
  public void atomicMoveNew(Path source, Path target) {
    try {
      Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
    } catch (IOException exception) {
      throw new StorageException("storage-atomic-move-failed", exception);
    }
  }

  @Override
  public boolean exists(Path path) {
    return Files.exists(path, LinkOption.NOFOLLOW_LINKS);
  }

  @Override
  public void deleteIfExists(Path path) {
    try {
      if (Files.isSymbolicLink(path)) {
        throw new StorageException("storage-link-rejected");
      }
      Files.deleteIfExists(path);
    } catch (IOException exception) {
      throw new StorageException("storage-delete-failed", exception);
    }
  }

  @Override
  public long usableSpace(Path path) {
    try {
      return Files.getFileStore(path).getUsableSpace();
    } catch (IOException exception) {
      throw new StorageException("storage-space-check-failed", exception);
    }
  }

  @Override
  public List<Path> list(Path directory) {
    try {
      if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)
          || Files.isSymbolicLink(directory)) {
        return List.of();
      }
      try (var paths = Files.list(directory)) {
        return paths.toList();
      }
    } catch (IOException exception) {
      throw new StorageException("storage-list-failed", exception);
    }
  }

  private void createParent(Path path) throws IOException {
    Path parent = path.getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
  }
}
