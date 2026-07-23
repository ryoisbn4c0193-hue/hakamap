package jp.hakamap.project.domain.value;

public record AssetDisplayName(String value) {
  public AssetDisplayName(String value) {
    this.value = TextValues.requiredSingleLine(value, 100, "invalid-asset-display-name");
  }
}
