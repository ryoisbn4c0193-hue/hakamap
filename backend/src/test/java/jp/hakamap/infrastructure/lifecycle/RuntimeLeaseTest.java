package jp.hakamap.infrastructure.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class RuntimeLeaseTest {
  @TempDir Path temporaryDirectory;

  private final JsonMapper jsonMapper = JsonMapper.builder().findAndAddModules().build();

  @Test
  void preventsDuplicateLeaseAndCleansRuntimeFilesOnClose() {
    RuntimePaths paths = new RuntimePaths(temporaryDirectory.resolve("runtime"));
    RuntimeLease first = RuntimeLease.tryAcquire(paths, jsonMapper).orElseThrow();

    assertThat(RuntimeLease.tryAcquire(paths, jsonMapper)).isEmpty();

    RuntimeInstance instance =
        new RuntimeInstance(
            123,
            UUID.fromString("11111111-1111-4111-8111-111111111111"),
            54321,
            Instant.parse("2026-01-02T03:04:05Z"),
            "control-token");
    first.writeInstance(instance);
    first.writeMarker(
        new UncleanExitMarker(
            instance.instanceId(), "1.0.0", instance.startedAt(), instance.startedAt()));

    assertThat(Files.exists(paths.instanceFile())).isTrue();
    assertThat(Files.exists(paths.uncleanExitMarker())).isTrue();
    first.close();
    assertThat(Files.exists(paths.instanceFile())).isFalse();
    assertThat(Files.exists(paths.uncleanExitMarker())).isFalse();

    assertThat(RuntimeLease.tryAcquire(paths, jsonMapper))
        .isPresent()
        .get()
        .satisfies(RuntimeLease::close);
  }
}
