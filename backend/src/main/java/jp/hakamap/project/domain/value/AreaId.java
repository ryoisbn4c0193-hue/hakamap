package jp.hakamap.project.domain.value;

import java.util.UUID;

public record AreaId(UUID value) {
  public AreaId(UUID value) {
    this.value = DomainIds.requireVersion4(value);
  }
}
