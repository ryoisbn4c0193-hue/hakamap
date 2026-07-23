package jp.hakamap.project.domain.value;

public record PosthumousName(String value) {
  public PosthumousName(String value) {
    this.value = TextValues.requiredSingleLine(value, 50, "invalid-posthumous-name");
  }
}
