package jp.hakamap.project.domain.value;

import java.math.BigDecimal;

public record MapRectangle(MapPoint topLeft, MapSize size) {
  private static final BigDecimal TWO = BigDecimal.valueOf(2);

  public MapRectangle {
    if (topLeft == null || size == null) {
      throw new DomainValidationException("invalid-map-rectangle");
    }
  }

  public MapRectangle(BigDecimal x, BigDecimal y, BigDecimal width, BigDecimal height) {
    this(new MapPoint(x, y), new MapSize(width, height));
  }

  public BigDecimal left() {
    return topLeft.x();
  }

  public BigDecimal top() {
    return topLeft.y();
  }

  public BigDecimal right() {
    return left().add(size.width());
  }

  public BigDecimal bottom() {
    return top().add(size.height());
  }

  public MapPoint center() {
    return new MapPoint(left().add(size.width().divide(TWO)), top().add(size.height().divide(TWO)));
  }

  public boolean containsClosed(MapPoint point) {
    return point.x().compareTo(left()) >= 0
        && point.x().compareTo(right()) <= 0
        && point.y().compareTo(top()) >= 0
        && point.y().compareTo(bottom()) <= 0;
  }

  public boolean containsClosed(MapRectangle other) {
    return other.left().compareTo(left()) >= 0
        && other.right().compareTo(right()) <= 0
        && other.top().compareTo(top()) >= 0
        && other.bottom().compareTo(bottom()) <= 0;
  }

  public boolean overlapsArea(MapRectangle other) {
    return left().compareTo(other.right()) < 0
        && right().compareTo(other.left()) > 0
        && top().compareTo(other.bottom()) < 0
        && bottom().compareTo(other.top()) > 0;
  }

  public boolean touches(MapRectangle other) {
    return !overlapsArea(other)
        && left().compareTo(other.right()) <= 0
        && right().compareTo(other.left()) >= 0
        && top().compareTo(other.bottom()) <= 0
        && bottom().compareTo(other.top()) >= 0;
  }

  public BigDecimal verticalOverlap(MapRectangle other) {
    BigDecimal start = top().max(other.top());
    BigDecimal end = bottom().min(other.bottom());
    return end.subtract(start).max(BigDecimal.ZERO);
  }

  public MapRectangle translate(BigDecimal deltaX, BigDecimal deltaY) {
    return new MapRectangle(topLeft.translate(deltaX, deltaY), size);
  }
}
