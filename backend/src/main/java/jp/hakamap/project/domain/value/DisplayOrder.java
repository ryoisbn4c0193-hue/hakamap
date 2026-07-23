package jp.hakamap.project.domain.value;

public record DisplayOrder(int value) implements Comparable<DisplayOrder> {
  public DisplayOrder {
    if (value < 0) {
      throw new DomainValidationException("invalid-display-order");
    }
  }

  @Override
  public int compareTo(DisplayOrder other) {
    return Integer.compare(value, other.value);
  }
}
