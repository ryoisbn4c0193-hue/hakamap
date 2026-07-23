package jp.hakamap.project.domain.model;

import java.util.Objects;
import java.util.Optional;

final class ListCopies {
  private ListCopies() {}

  static <T> Optional<T> optional(Optional<T> value) {
    return Objects.requireNonNull(value, "optional");
  }
}
