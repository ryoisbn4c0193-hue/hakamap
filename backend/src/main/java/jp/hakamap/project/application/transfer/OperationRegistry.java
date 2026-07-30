package jp.hakamap.project.application.transfer;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class OperationRegistry implements AutoCloseable {
  private static final Logger LOGGER = LoggerFactory.getLogger(OperationRegistry.class);

  private static final Duration RETENTION = Duration.ofMinutes(10);

  private final ExecutorService executor = Executors.newSingleThreadExecutor();

  private final Map<String, Operation> operations = new LinkedHashMap<>();

  private final Set<String> reservations = new HashSet<>();

  private final Clock clock;

  public OperationRegistry(Clock clock) {
    this.clock = clock;
  }

  public synchronized OperationView start(
      String sessionId, boolean cancellable, Supplier<UUID> action) {
    return start(sessionId, cancellable, UUID.randomUUID().toString(), action);
  }

  public synchronized OperationView start(
      String sessionId, boolean cancellable, String reservationKey, Supplier<UUID> action) {
    discardExpired();
    if (!reservations.add(reservationKey)) {
      throw new ProjectTransferException("project-busy");
    }
    String id = UUID.randomUUID().toString();
    Operation operation = new Operation(id, sessionId, reservationKey, cancellable);
    operations.put(id, operation);
    executor.submit(() -> execute(operation, action));
    return view(operation);
  }

  public synchronized OperationView get(String id, String sessionId) {
    discardExpired();
    return view(require(id, sessionId));
  }

  public synchronized OperationView cancel(String id, String sessionId) {
    Operation operation = require(id, sessionId);
    if (!operation.cancellable || !"queued".equals(operation.status)) {
      throw new ProjectTransferException("operation-cancel-unavailable");
    }
    operation.status = "cancelled";
    operation.cancellable = false;
    operation.completedAt = clock.instant();
    reservations.remove(operation.reservationKey);
    return view(operation);
  }

  private void execute(Operation operation, Supplier<UUID> action) {
    synchronized (this) {
      if ("cancelled".equals(operation.status)) {
        return;
      }
      operation.status = "running";
      operation.cancellable = false;
    }
    try {
      UUID projectId = action.get();
      synchronized (this) {
        operation.cancellable = false;
        operation.status = "succeeded";
        operation.projectId = projectId;
        operation.completedAt = clock.instant();
        reservations.remove(operation.reservationKey);
      }
    } catch (RuntimeException exception) {
      synchronized (this) {
        operation.cancellable = false;
        operation.status = "failed";
        operation.errorCode =
            exception instanceof ProjectTransferException
                ? exception.getMessage()
                : "internal-unexpected";
        operation.completedAt = clock.instant();
        reservations.remove(operation.reservationKey);
        if (!(exception instanceof ProjectTransferException)) {
          LOGGER.error("operation-failed: {}", exception.getClass().getSimpleName());
        }
      }
    }
  }

  private Operation require(String id, String sessionId) {
    Operation operation = operations.get(id);
    if (operation == null || !operation.sessionId.equals(sessionId)) {
      throw new ProjectTransferException("operation-not-found");
    }
    return operation;
  }

  private OperationView view(Operation operation) {
    return new OperationView(
        operation.id,
        operation.status,
        operation.cancellable,
        "queued".equals(operation.status) ? "waiting" : "fileProcessing",
        null,
        operation.projectId,
        operation.errorCode);
  }

  private void discardExpired() {
    Instant threshold = clock.instant().minus(RETENTION);
    operations
        .values()
        .removeIf(
            operation ->
                operation.completedAt != null && operation.completedAt.isBefore(threshold));
  }

  @Override
  public void close() {
    executor.shutdownNow();
  }

  public record OperationView(
      String operationId,
      String status,
      boolean cancellable,
      String phaseCode,
      Integer progressPercent,
      UUID projectId,
      String errorCode) {}

  private static final class Operation {
    private final String id;

    private final String sessionId;

    private final String reservationKey;

    private String status = "queued";

    private boolean cancellable;

    private UUID projectId;

    private String errorCode;

    private Instant completedAt;

    private Operation(String id, String sessionId, String reservationKey, boolean cancellable) {
      this.id = id;
      this.sessionId = sessionId;
      this.reservationKey = reservationKey;
      this.cancellable = cancellable;
    }
  }
}
