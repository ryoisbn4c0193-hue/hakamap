package jp.hakamap.project.domain.value;

public record ManagementNumber(String value) {
  public ManagementNumber(String value) {
    this.value = TextValues.requiredSingleLine(value, 25, "invalid-management-number");
  }
}
