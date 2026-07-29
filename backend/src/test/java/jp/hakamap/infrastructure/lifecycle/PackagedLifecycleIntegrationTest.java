package jp.hakamap.infrastructure.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import jp.hakamap.HakamapApplication;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import tools.jackson.databind.json.JsonMapper;

class PackagedLifecycleIntegrationTest {
  @TempDir Path temporaryDirectory;

  @Test
  void startsPackagedLifecycleAndCleansRuntimeState() throws Exception {
    JsonMapper jsonMapper = JsonMapper.builder().findAndAddModules().build();
    RuntimePaths paths = new RuntimePaths(temporaryDirectory.resolve("runtime"));
    RuntimeLease lease = RuntimeLease.tryAcquire(paths, jsonMapper).orElseThrow();
    AtomicReference<URI> openedUri = new AtomicReference<>();
    SpringApplication application = new SpringApplication(HakamapApplication.class);
    application.setDefaultProperties(
        Map.of(
            "server.address", "127.0.0.1", "server.port", "0", "spring.main.banner-mode", "off"));
    application.addInitializers(
        context -> {
          context.getBeanFactory().registerSingleton("runtimeLease", lease);
          context
              .getBeanFactory()
              .registerSingleton("browserLauncher", (BrowserLauncher) openedUri::set);
        });

    ConfigurableApplicationContext context = application.run();
    try {
      assertThat(context.getBean(RuntimeLifecycleService.class)).isNotNull();
      RuntimeInstance instance =
          jsonMapper.readValue(Files.readAllBytes(paths.instanceFile()), RuntimeInstance.class);
      assertThat(instance.port()).isPositive();
      assertThat(openedUri.get().getHost()).isEqualTo("127.0.0.1");
      assertThat(openedUri.get().getPort()).isEqualTo(instance.port());
      assertThat(openedUri.get().getQuery()).isNull();
      assertThat(openedUri.get().getFragment()).startsWith("bootstrap=");
    } finally {
      context.close();
    }

    assertThat(Files.exists(paths.instanceFile())).isFalse();
    assertThat(Files.exists(paths.uncleanExitMarker())).isFalse();
    assertThat(RuntimeLease.tryAcquire(paths, jsonMapper))
        .isPresent()
        .get()
        .satisfies(RuntimeLease::close);
  }
}
