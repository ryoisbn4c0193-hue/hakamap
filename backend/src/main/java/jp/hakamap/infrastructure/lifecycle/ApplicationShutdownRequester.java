package jp.hakamap.infrastructure.lifecycle;

@FunctionalInterface
public interface ApplicationShutdownRequester {
  void requestShutdown();
}
