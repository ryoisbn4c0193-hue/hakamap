package jp.hakamap.project.domain.value;

public record AreaName(String value) {
  public AreaName(String value) {
    this.value = TextValues.requiredSingleLine(value, 25, "invalid-area-name");
  }
}
