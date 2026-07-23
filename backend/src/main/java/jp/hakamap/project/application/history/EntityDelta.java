package jp.hakamap.project.application.history;

import java.util.Objects;
import java.util.Optional;

public record EntityDelta<I, T>(I id, Optional<T> before, Optional<T> after) {
  public EntityDelta {
    Objects.requireNonNull(id, "id");
    before = Objects.requireNonNull(before, "before");
    after = Objects.requireNonNull(after, "after");
    if (before.isEmpty() && after.isEmpty()) {
      throw new IllegalArgumentException("empty-entity-delta");
    }
  }

  public static <I, T> EntityDelta<I, T> created(I id, T after) {
    return new EntityDelta<>(id, Optional.empty(), Optional.of(after));
  }

  public static <I, T> EntityDelta<I, T> updated(I id, T before, T after) {
    return new EntityDelta<>(id, Optional.of(before), Optional.of(after));
  }

  public static <I, T> EntityDelta<I, T> deleted(I id, T before) {
    return new EntityDelta<>(id, Optional.of(before), Optional.empty());
  }
}
