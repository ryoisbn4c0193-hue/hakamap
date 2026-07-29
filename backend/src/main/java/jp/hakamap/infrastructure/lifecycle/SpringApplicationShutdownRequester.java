package jp.hakamap.infrastructure.lifecycle;

import org.springframework.context.ConfigurableApplicationContext;

public final class SpringApplicationShutdownRequester implements ApplicationShutdownRequester {
  private final ConfigurableApplicationContext context;

  public SpringApplicationShutdownRequester(ConfigurableApplicationContext context) {
    this.context = context;
  }

  @Override
  public void requestShutdown() {
    Thread shutdownThread =
        Thread.ofPlatform()
            .name("hakamap-requested-shutdown")
            .unstarted(
                () -> {
                  try {
                    Thread.sleep(100);
                  } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                  }
                  context.close();
                });
    shutdownThread.start();
  }
}
