package jp.hakamap.project.infrastructure.storage;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public final class ProjectFileLock implements AutoCloseable {
  private final FileChannel channel;

  private final FileLock lock;

  private ProjectFileLock(FileChannel channel, FileLock lock) {
    this.channel = channel;
    this.lock = lock;
  }

  public static ProjectFileLock acquire(Path projectRoot) {
    Path lockPath = projectRoot.resolve(".hakamap.lock").toAbsolutePath().normalize();
    try {
      Files.createDirectories(projectRoot);
      if (Files.isSymbolicLink(lockPath)) {
        throw new StorageException("project-lock-link-rejected");
      }
      FileChannel channel =
          FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
      try {
        FileLock lock = channel.tryLock();
        if (lock == null) {
          channel.close();
          throw new StorageException("project-already-locked");
        }
        return new ProjectFileLock(channel, lock);
      } catch (OverlappingFileLockException exception) {
        channel.close();
        throw new StorageException("project-already-locked", exception);
      }
    } catch (IOException exception) {
      throw new StorageException("project-lock-failed", exception);
    }
  }

  public boolean valid() {
    return channel.isOpen() && lock.isValid();
  }

  @Override
  public void close() {
    try {
      lock.release();
      channel.close();
    } catch (IOException exception) {
      throw new StorageException("project-lock-release-failed", exception);
    }
  }
}
