package jp.hakamap.project.domain.value;

public record AssetDescription(String value) {
  public AssetDescription(String value) {
    this.value =
        TextValues.optionalMultiLine(value, 500, "invalid-asset-description")
            .orElseThrow(() -> new DomainValidationException("invalid-asset-description"));
  }
}
