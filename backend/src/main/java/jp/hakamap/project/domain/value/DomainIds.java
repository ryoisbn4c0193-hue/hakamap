package jp.hakamap.project.domain.value;

import java.util.Objects;
import java.util.UUID;

final class DomainIds {
  private DomainIds() {}

  static UUID requireVersion4(UUID value) {
    Objects.requireNonNull(value, "value");
    if (value.version() != 4 || value.variant() != 2) {
      throw new DomainValidationException("invalid-uuid-v4");
    }
    return value;
  }
}
