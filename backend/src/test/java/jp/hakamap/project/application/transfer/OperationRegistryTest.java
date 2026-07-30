package jp.hakamap.project.application.transfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
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

  @Test
  void reservesProjectAndRejectsCompetingOperation() throws Exception {
    try (OperationRegistry operations =
        new OperationRegistry(Clock.fixed(Instant.parse("2026-07-30T00:00:00Z"), ZoneOffset.UTC))) {
      CountDownLatch release = new CountDownLatch(1);
      OperationRegistry.OperationView first =
          operations.start(
              "session-a",
              true,
              "project:one",
              () -> {
                await(release);
                return UUID.randomUUID();
              });

      assertThatThrownBy(
              () -> operations.start("session-a", true, "project:one", () -> UUID.randomUUID()))
          .isInstanceOf(ProjectTransferException.class)
          .hasMessage("project-busy");

      release.countDown();
      awaitTerminal(operations, first);
    }
  }

  @Test
  void runningOperationCanBeCancelledBeforeCommit() throws Exception {
    try (OperationRegistry operations =
        new OperationRegistry(Clock.fixed(Instant.parse("2026-07-30T00:00:00Z"), ZoneOffset.UTC))) {
      CountDownLatch started = new CountDownLatch(1);
      CountDownLatch release = new CountDownLatch(1);
      OperationRegistry.OperationView operation =
          operations.start(
              "session-a",
              true,
              "project:one",
              control -> {
                started.countDown();
                while (release.getCount() > 0) {
                  control.checkpoint();
                  Thread.onSpinWait();
                }
                return UUID.randomUUID();
              });
      assertThat(started.await(1, TimeUnit.SECONDS)).isTrue();

      OperationRegistry.OperationView running =
          operations.get(operation.operationId(), "session-a");
      assertThat(running.status()).isEqualTo("running");
      assertThat(running.cancellable()).isTrue();

      operations.cancel(operation.operationId(), "session-a");
      OperationRegistry.OperationView cancelled = operation;
      for (int attempt = 0; attempt < 100 && !"cancelled".equals(cancelled.status()); attempt++) {
        Thread.sleep(5);
        cancelled = operations.get(operation.operationId(), "session-a");
      }
      assertThat(cancelled.status()).isEqualTo("cancelled");

      release.countDown();
    }
  }

  @Test
  void committingOperationCannotBeCancelled() throws Exception {
    try (OperationRegistry operations =
        new OperationRegistry(Clock.fixed(Instant.parse("2026-07-30T00:00:00Z"), ZoneOffset.UTC))) {
      CountDownLatch committing = new CountDownLatch(1);
      CountDownLatch release = new CountDownLatch(1);
      OperationRegistry.OperationView operation =
          operations.start(
              "session-a",
              true,
              "project:one",
              control -> {
                control.beginCommit();
                committing.countDown();
                await(release);
                return UUID.randomUUID();
              });
      assertThat(committing.await(1, TimeUnit.SECONDS)).isTrue();

      OperationRegistry.OperationView result = operations.get(operation.operationId(), "session-a");
      assertThat(result.status()).isEqualTo("committing");
      assertThat(result.cancellable()).isFalse();
      assertThatThrownBy(() -> operations.cancel(operation.operationId(), "session-a"))
          .isInstanceOf(ProjectTransferException.class)
          .hasMessage("operation-cancel-unavailable");

      release.countDown();
      awaitTerminal(operations, operation);
    }
  }

  @Test
  void hidesUnexpectedExceptionDetails() throws Exception {
    try (OperationRegistry operations =
        new OperationRegistry(Clock.fixed(Instant.parse("2026-07-30T00:00:00Z"), ZoneOffset.UTC))) {
      OperationRegistry.OperationView operation =
          operations.start(
              "session-a",
              true,
              "project:one",
              () -> {
                throw new IllegalStateException("/secret/local/path");
              });

      OperationRegistry.OperationView result = operation;
      for (int attempt = 0; attempt < 100 && !"failed".equals(result.status()); attempt++) {
        Thread.sleep(5);
        result = operations.get(operation.operationId(), "session-a");
      }
      assertThat(result.errorCode()).isEqualTo("internal-unexpected");
    }
  }

  private void await(CountDownLatch latch) {
    try {
      if (!latch.await(1, TimeUnit.SECONDS)) {
        throw new IllegalStateException("test-timeout");
      }
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("test-interrupted", exception);
    }
  }

  private void awaitTerminal(
      OperationRegistry operations, OperationRegistry.OperationView operation)
      throws InterruptedException {
    OperationRegistry.OperationView result = operation;
    for (int attempt = 0;
        attempt < 100 && !java.util.Set.of("succeeded", "failed").contains(result.status());
        attempt++) {
      Thread.sleep(5);
      result = operations.get(operation.operationId(), "session-a");
    }
    assertThat(result.status()).isEqualTo("succeeded");
  }
}
