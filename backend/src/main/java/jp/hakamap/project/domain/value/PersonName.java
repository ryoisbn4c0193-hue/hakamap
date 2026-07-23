package jp.hakamap.project.domain.value;

public record PersonName(String value) {
  public PersonName(String value) {
    this.value = TextValues.requiredSingleLine(value, 50, "invalid-person-name");
  }
}
