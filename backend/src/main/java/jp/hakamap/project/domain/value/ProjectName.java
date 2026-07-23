package jp.hakamap.project.domain.value;

public record ProjectName(String value) {
  public ProjectName(String value) {
    this.value = TextValues.requiredSingleLine(value, 50, "invalid-project-name");
  }
}
