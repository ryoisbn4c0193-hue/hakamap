package jp.hakamap.infrastructure.lifecycle;

import java.net.URI;

@FunctionalInterface
public interface BrowserLauncher {
  void open(URI uri);
}
