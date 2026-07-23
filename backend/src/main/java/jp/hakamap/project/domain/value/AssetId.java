package jp.hakamap.project.domain.value;

import java.util.UUID;

public record AssetId(UUID value) {
  public AssetId(UUID value) {
    this.value = DomainIds.requireVersion4(value);
  }
}
