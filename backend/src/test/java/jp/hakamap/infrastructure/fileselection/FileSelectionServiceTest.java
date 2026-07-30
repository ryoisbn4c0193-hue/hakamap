package jp.hakamap.infrastructure.fileselection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileSelectionServiceTest {
  @TempDir Path temporaryDirectory;

  @Test
  void returnsOnlyDisplayNameAndConsumesSelectionOnceForBoundSessionAndPurpose() {
    Path selected = temporaryDirectory.resolve("保存先");
    selected.toFile().mkdirs();
    FileSelectionService service =
        new FileSelectionService(
            ignored -> List.of(selected),
            Clock.fixed(Instant.parse("2026-07-29T00:00:00Z"), ZoneOffset.UTC));

    var result =
        service.start(
            "session-a",
            FileSelectionMode.DIRECTORY,
            FileSelectionPurpose.PROJECT_CREATE_DIRECTORY);

    assertThat(result.status()).isEqualTo("selected");
    assertThat(result.displayNames()).containsExactly("保存先");
    assertThat(result.displayNames().getFirst()).doesNotContain(temporaryDirectory.toString());
    assertThat(
            service.consume(
                result.fileSelectionIds().getFirst(),
                "session-a",
                FileSelectionPurpose.PROJECT_CREATE_DIRECTORY))
        .isEqualTo(selected.toAbsolutePath().normalize());
    assertThatThrownBy(
            () ->
                service.consume(
                    result.fileSelectionIds().getFirst(),
                    "session-a",
                    FileSelectionPurpose.PROJECT_CREATE_DIRECTORY))
        .isInstanceOf(FileSelectionException.class)
        .hasMessage("file-selection-not-found");
  }

  @Test
  void rejectsAnotherSessionPurposeAndExpiredSelectionWithoutRevealingExistence() {
    Path selected = temporaryDirectory.resolve("selected");
    selected.toFile().mkdirs();
    Instant selectedAt = Instant.parse("2026-07-29T00:00:00Z");
    MutableClock clock = new MutableClock(selectedAt);
    FileSelectionService service = new FileSelectionService(ignored -> List.of(selected), clock);
    var result =
        service.start(
            "session-a",
            FileSelectionMode.DIRECTORY,
            FileSelectionPurpose.PROJECT_CREATE_DIRECTORY);

    assertThatThrownBy(
            () ->
                service.consume(
                    result.fileSelectionIds().getFirst(),
                    "session-b",
                    FileSelectionPurpose.PROJECT_CREATE_DIRECTORY))
        .isInstanceOf(FileSelectionException.class)
        .hasMessage("file-selection-not-found");
    clock.advanceSeconds(301);
    assertThatThrownBy(
            () ->
                service.consume(
                    result.fileSelectionIds().getFirst(),
                    "session-a",
                    FileSelectionPurpose.PROJECT_CREATE_DIRECTORY))
        .isInstanceOf(FileSelectionException.class);
  }

  @Test
  void cancellationIsNormalResultAndInvalidModeCombinationIsRejected() {
    FileSelectionService service =
        new FileSelectionService(
            ignored -> List.of(),
            Clock.fixed(Instant.parse("2026-07-29T00:00:00Z"), ZoneOffset.UTC));

    assertThat(
            service
                .start(
                    "session-a",
                    FileSelectionMode.DIRECTORY,
                    FileSelectionPurpose.PROJECT_CREATE_DIRECTORY)
                .status())
        .isEqualTo("cancelled");
    assertThatThrownBy(
            () ->
                service.start(
                    "session-a",
                    FileSelectionMode.SINGLE_FILE,
                    FileSelectionPurpose.PROJECT_CREATE_DIRECTORY))
        .isInstanceOf(FileSelectionException.class)
        .hasMessage("file-selection-request-invalid");
  }

  @Test
  void acceptsNewFilePathForExportDestination() {
    Path destination = temporaryDirectory.resolve("export.hakamap");
    FileSelectionService service =
        new FileSelectionService(
            ignored -> List.of(destination),
            Clock.fixed(Instant.parse("2026-07-29T00:00:00Z"), ZoneOffset.UTC));

    var result =
        service.start(
            "session-a", FileSelectionMode.SINGLE_FILE, FileSelectionPurpose.EXPORT_DESTINATION);

    assertThat(
            service.consume(
                result.fileSelectionIds().getFirst(),
                "session-a",
                FileSelectionPurpose.EXPORT_DESTINATION))
        .isEqualTo(destination.toAbsolutePath().normalize());
  }

  private static final class MutableClock extends Clock {
    private Instant now;

    private MutableClock(Instant now) {
      this.now = now;
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }

    private void advanceSeconds(long seconds) {
      now = now.plusSeconds(seconds);
    }
  }
}
