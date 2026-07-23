package jp.hakamap.project.application.command;

import java.time.Instant;
import java.util.Objects;
import jp.hakamap.project.domain.service.UuidSource;

public record CommandContext(
    long expectedRevision, Instant commandTimestamp, UuidSource uuidSource) {
  public CommandContext {
    if (expectedRevision < 0) {
      throw new IllegalArgumentException("invalid-expected-revision");
    }
    Objects.requireNonNull(commandTimestamp, "commandTimestamp");
    Objects.requireNonNull(uuidSource, "uuidSource");
  }
}
