package jp.hakamap.infrastructure.lifecycle;

import java.util.Map;
import jp.hakamap.HakamapApplication;
import org.springframework.boot.SpringApplication;
import tools.jackson.databind.json.JsonMapper;

public final class ApplicationLauncher {
  private ApplicationLauncher() {}

  public static void launch(String[] args) {
    JsonMapper jsonMapper = JsonMapper.builder().findAndAddModules().build();
    RuntimePaths paths = RuntimePaths.forCurrentUser(System.getenv());
    var acquired = RuntimeLease.tryAcquire(paths, jsonMapper);
    if (acquired.isEmpty()) {
      if (!new ExistingInstanceClient(jsonMapper).requestReopen(paths)) {
        throw new LifecycleException("existing-instance-unavailable");
      }
      return;
    }
    RuntimeLease lease = acquired.orElseThrow();
    SpringApplication application = new SpringApplication(HakamapApplication.class);
    application.setHeadless(false);
    application.setDefaultProperties(Map.of("server.address", "127.0.0.1", "server.port", "0"));
    application.addInitializers(
        context -> {
          context.getBeanFactory().registerSingleton("runtimeLease", lease);
          context
              .getBeanFactory()
              .registerSingleton("browserLauncher", new DesktopBrowserLauncher());
        });
    try {
      application.run(args);
    } catch (RuntimeException exception) {
      lease.close();
      throw exception;
    }
  }
}
