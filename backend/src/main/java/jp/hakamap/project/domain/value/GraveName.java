package jp.hakamap.project.domain.value;

public record GraveName(String value) {
  public GraveName(String value) {
    this.value = TextValues.requiredSingleLine(value, 50, "invalid-grave-name");
  }
}
