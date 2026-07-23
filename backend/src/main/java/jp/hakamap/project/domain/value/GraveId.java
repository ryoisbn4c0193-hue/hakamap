package jp.hakamap.project.domain.value;

import java.util.UUID;

public record GraveId(UUID value) {
  public GraveId(UUID value) {
    this.value = DomainIds.requireVersion4(value);
  }
}
