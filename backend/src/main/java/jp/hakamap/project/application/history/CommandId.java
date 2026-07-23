package jp.hakamap.project.application.history;

import java.util.Objects;
import java.util.UUID;

public record CommandId(UUID value) {
  public CommandId {
    Objects.requireNonNull(value, "value");
    if (value.version() != 4) {
      throw new IllegalArgumentException("invalid-command-id");
    }
  }
}
