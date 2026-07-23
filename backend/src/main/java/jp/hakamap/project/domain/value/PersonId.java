package jp.hakamap.project.domain.value;

import java.util.UUID;

public record PersonId(UUID value) {
  public PersonId(UUID value) {
    this.value = DomainIds.requireVersion4(value);
  }
}
