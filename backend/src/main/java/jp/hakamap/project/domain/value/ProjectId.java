package jp.hakamap.project.domain.value;

import java.util.UUID;

public record ProjectId(UUID value) {
  public ProjectId(UUID value) {
    this.value = DomainIds.requireVersion4(value);
  }
}
