package jp.hakamap.project.application.history;

import java.util.Objects;
import java.util.Optional;

public record ValueDelta<T>(Optional<T> before, Optional<T> after) {
  public ValueDelta {
    before = Objects.requireNonNull(before, "before");
    after = Objects.requireNonNull(after, "after");
    if (before.equals(after)) {
      throw new IllegalArgumentException("unchanged-value-delta");
    }
  }

  public static <T> ValueDelta<T> changed(T before, T after) {
    return new ValueDelta<>(Optional.of(before), Optional.of(after));
  }
}
