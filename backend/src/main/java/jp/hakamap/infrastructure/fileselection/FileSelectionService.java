package jp.hakamap.infrastructure.fileselection;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public final class FileSelectionService {
  private static final Duration LIFETIME = Duration.ofMinutes(5);

  private final FileChooserGateway chooser;

  private final Clock clock;

  private final Map<UUID, SelectionContext> selections = new LinkedHashMap<>();

  private final AtomicBoolean dialogOpen = new AtomicBoolean();

  public FileSelectionService(FileChooserGateway chooser, Clock clock) {
    this.chooser = chooser;
    this.clock = clock;
  }

  public FileSelectionResult start(
      String sessionId, FileSelectionMode mode, FileSelectionPurpose purpose) {
    if (mode != purpose.requiredMode()) {
      throw new FileSelectionException("file-selection-request-invalid");
    }
    if (!dialogOpen.compareAndSet(false, true)) {
      throw new FileSelectionException("file-selection-dialog-already-open");
    }
    try {
      discardExpired();
      List<Path> chosen = chooser.choose(mode, purpose);
      if (chosen.isEmpty()) {
        return new FileSelectionResult("cancelled", List.of(), List.of());
      }
      List<UUID> ids = new ArrayList<>();
      List<String> names = new ArrayList<>();
      for (Path path : chosen) {
        Path normalized = path.toAbsolutePath().normalize();
        UUID id = UUID.randomUUID();
        register(
            id,
            new SelectionContext(sessionId, purpose, normalized, clock.instant().plus(LIFETIME)));
        ids.add(id);
        names.add(normalized.getFileName().toString());
      }
      return new FileSelectionResult("selected", ids, names);
    } finally {
      dialogOpen.set(false);
    }
  }

  public synchronized Path consume(UUID id, String sessionId, FileSelectionPurpose purpose) {
    discardExpired();
    SelectionContext context = selections.get(id);
    if (context == null || !context.sessionId().equals(sessionId) || context.purpose() != purpose) {
      throw new FileSelectionException("file-selection-not-found");
    }
    selections.remove(id);
    Path path = context.path();
    boolean valid;
    if (purpose == FileSelectionPurpose.EXPORT_DESTINATION) {
      valid =
          !Files.isDirectory(path)
              && path.getParent() != null
              && Files.isDirectory(path.getParent())
              && !Files.isSymbolicLink(path.getParent());
    } else {
      valid =
          purpose.requiredMode() == FileSelectionMode.DIRECTORY
              ? Files.isDirectory(path)
              : Files.isRegularFile(path);
    }
    if (!valid || Files.isSymbolicLink(path)) {
      throw new FileSelectionException("file-selection-invalid");
    }
    return path;
  }

  public synchronized void invalidate(UUID id, String sessionId) {
    SelectionContext context = selections.get(id);
    if (context == null || !context.sessionId().equals(sessionId)) {
      throw new FileSelectionException("file-selection-not-found");
    }
    selections.remove(id);
  }

  public synchronized void invalidateSession(String sessionId) {
    selections.entrySet().removeIf(entry -> entry.getValue().sessionId().equals(sessionId));
  }

  private synchronized void register(UUID id, SelectionContext context) {
    selections.put(id, context);
  }

  private synchronized void discardExpired() {
    Instant now = clock.instant();
    selections.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().expiresAt()));
  }

  private record SelectionContext(
      String sessionId, FileSelectionPurpose purpose, Path path, Instant expiresAt) {}

  public record FileSelectionResult(
      String status, List<UUID> fileSelectionIds, List<String> displayNames) {}
}
