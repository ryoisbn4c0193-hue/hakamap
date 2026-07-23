package jp.hakamap.project.domain.value;

public record TextComparisonKey(String value) {
  public TextComparisonKey {
    if (value == null || value.isEmpty()) {
      throw new DomainValidationException("invalid-comparison-key");
    }
  }
}
