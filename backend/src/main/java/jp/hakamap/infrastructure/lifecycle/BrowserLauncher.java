package jp.hakamap.infrastructure.lifecycle;

import java.net.URI;

@FunctionalInterface
public interface BrowserLauncher extends AutoCloseable {
  void open(URI uri);

  @Override
  default void close() {}
}
