package jp.hakamap.infrastructure.lifecycle;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.util.UUID;
import tools.jackson.databind.json.JsonMapper;

public final class RuntimeLease implements AutoCloseable {
  private final RuntimePaths paths;

  private final FileChannel lockChannel;

  private final FileLock lock;

  private final JsonMapper jsonMapper;

  private boolean closed;

  private RuntimeLease(
      RuntimePaths paths, FileChannel lockChannel, FileLock lock, JsonMapper jsonMapper) {
    this.paths = paths;
    this.lockChannel = lockChannel;
    this.lock = lock;
    this.jsonMapper = jsonMapper;
  }

  public static Optional<RuntimeLease> tryAcquire(RuntimePaths paths, JsonMapper jsonMapper) {
    SecureRuntimeFiles.secureDirectory(paths.directory());
    try {
      FileChannel channel =
          FileChannel.open(
              paths.applicationLock(),
              StandardOpenOption.CREATE,
              StandardOpenOption.READ,
              StandardOpenOption.WRITE);
      SecureRuntimeFiles.secureFile(paths.applicationLock());
      FileLock lock;
      try {
        lock = channel.tryLock();
      } catch (java.nio.channels.OverlappingFileLockException exception) {
        lock = null;
      }
      if (lock == null) {
        channel.close();
        return Optional.empty();
      }
      Files.deleteIfExists(paths.instanceFile());
      return Optional.of(new RuntimeLease(paths, channel, lock, jsonMapper));
    } catch (IOException exception) {
      throw new LifecycleException("application-lock-failed", exception);
    }
  }

  public RuntimePaths paths() {
    return paths;
  }

  public boolean previousExitWasUnclean() {
    return Files.exists(paths.uncleanExitMarker());
  }

  public void writeInstance(RuntimeInstance instance) {
    writeAtomically(paths.instanceFile(), instance);
  }

  public void writeMarker(UncleanExitMarker marker) {
    writeAtomically(paths.uncleanExitMarker(), marker);
  }

  private void writeAtomically(Path target, Object value) {
    Path temporary = target.resolveSibling("." + target.getFileName() + "-" + UUID.randomUUID());
    try {
      byte[] bytes = jsonMapper.writeValueAsBytes(value);
      try (FileChannel channel =
          FileChannel.open(temporary, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
        channel.write(ByteBuffer.wrap(bytes));
        channel.force(true);
      }
      SecureRuntimeFiles.secureFile(temporary);
      Files.move(
          temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException | RuntimeException exception) {
      try {
        Files.deleteIfExists(temporary);
      } catch (IOException ignored) {
        // 起動時に既知の一時ファイルとして清掃する。
      }
      throw new LifecycleException("runtime-file-write-failed", exception);
    }
  }

  @Override
  public void close() {
    if (closed) {
      return;
    }
    closed = true;
    try {
      Files.deleteIfExists(paths.instanceFile());
      Files.deleteIfExists(paths.uncleanExitMarker());
    } catch (IOException ignored) {
      // 終了時の清掃失敗はユーザーデータの保存状態へ影響させない。
    }
    try {
      lock.release();
      lockChannel.close();
    } catch (IOException ignored) {
      // プロセス終了時にOSが解放する。
    }
  }
}
