package jp.hakamap.project.application.transfer;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OperationRegistryTest {
  @Test
  void bindsOperationToSessionAndPublishesResult() throws Exception {
    try (OperationRegistry operations =
        new OperationRegistry(Clock.fixed(Instant.parse("2026-07-30T00:00:00Z"), ZoneOffset.UTC))) {
      UUID projectId = UUID.randomUUID();
      OperationRegistry.OperationView started =
          operations.start("session-a", true, () -> projectId);

      OperationRegistry.OperationView result = started;
      for (int attempt = 0; attempt < 100 && !"succeeded".equals(result.status()); attempt++) {
        Thread.sleep(5);
        result = operations.get(started.operationId(), "session-a");
      }

      assertThat(result.status()).isEqualTo("succeeded");
      assertThat(result.projectId()).isEqualTo(projectId);
    }
  }
}
