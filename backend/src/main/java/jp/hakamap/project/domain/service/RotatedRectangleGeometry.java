package jp.hakamap.project.domain.service;

import java.util.ArrayList;
import java.util.List;
import jp.hakamap.project.domain.value.MapPoint;
import jp.hakamap.project.domain.value.MapRectangle;
import jp.hakamap.project.domain.value.RotationDegrees;

/** 中心基準で回転した長方形の包含と面積を持つ交差を判定する。 */
public final class RotatedRectangleGeometry {
  private static final double EPSILON = 0.000_000_1;

  public boolean containsClosed(MapRectangle rectangle, RotationDegrees rotation, MapPoint point) {
    Point local = inverseRotate(point(point), center(rectangle), degrees(rotation));
    return local.x() >= rectangle.left().doubleValue() - EPSILON
        && local.x() <= rectangle.right().doubleValue() + EPSILON
        && local.y() >= rectangle.top().doubleValue() - EPSILON
        && local.y() <= rectangle.bottom().doubleValue() + EPSILON;
  }

  public boolean containsClosed(
      MapRectangle container,
      RotationDegrees containerRotation,
      MapRectangle target,
      RotationDegrees targetRotation) {
    return corners(target, targetRotation).stream()
        .allMatch(
            corner ->
                containsClosed(
                    container,
                    containerRotation,
                    new MapPoint(
                        java.math.BigDecimal.valueOf(corner.x()),
                        java.math.BigDecimal.valueOf(corner.y()))));
  }

  public boolean overlapsArea(
      MapRectangle first,
      RotationDegrees firstRotation,
      MapRectangle second,
      RotationDegrees secondRotation) {
    List<Point> firstCorners = corners(first, firstRotation);
    List<Point> secondCorners = corners(second, secondRotation);
    List<Point> axes = new ArrayList<>();
    axes.addAll(axes(firstCorners));
    axes.addAll(axes(secondCorners));
    return axes.stream().noneMatch(axis -> separated(firstCorners, secondCorners, axis));
  }

  Bounds bounds(MapRectangle rectangle, RotationDegrees rotation) {
    List<Point> points = corners(rectangle, rotation);
    return new Bounds(
        points.stream().mapToDouble(Point::x).min().orElseThrow(),
        points.stream().mapToDouble(Point::y).min().orElseThrow(),
        points.stream().mapToDouble(Point::x).max().orElseThrow(),
        points.stream().mapToDouble(Point::y).max().orElseThrow());
  }

  private boolean separated(List<Point> first, List<Point> second, Point axis) {
    Projection firstProjection = project(first, axis);
    Projection secondProjection = project(second, axis);
    return firstProjection.maximum() <= secondProjection.minimum() + EPSILON
        || secondProjection.maximum() <= firstProjection.minimum() + EPSILON;
  }

  private Projection project(List<Point> points, Point axis) {
    double minimum = Double.POSITIVE_INFINITY;
    double maximum = Double.NEGATIVE_INFINITY;
    for (Point point : points) {
      double value = point.x() * axis.x() + point.y() * axis.y();
      minimum = Math.min(minimum, value);
      maximum = Math.max(maximum, value);
    }
    return new Projection(minimum, maximum);
  }

  private List<Point> axes(List<Point> corners) {
    List<Point> result = new ArrayList<>(2);
    for (int index = 0; index < 2; index++) {
      Point start = corners.get(index);
      Point end = corners.get(index + 1);
      double edgeX = end.x() - start.x();
      double edgeY = end.y() - start.y();
      double length = Math.hypot(edgeX, edgeY);
      result.add(new Point(-edgeY / length, edgeX / length));
    }
    return result;
  }

  private List<Point> corners(MapRectangle rectangle, RotationDegrees rotation) {
    Point center = center(rectangle);
    double radians = Math.toRadians(degrees(rotation));
    return List.of(
        rotate(
            new Point(rectangle.left().doubleValue(), rectangle.top().doubleValue()),
            center,
            radians),
        rotate(
            new Point(rectangle.right().doubleValue(), rectangle.top().doubleValue()),
            center,
            radians),
        rotate(
            new Point(rectangle.right().doubleValue(), rectangle.bottom().doubleValue()),
            center,
            radians),
        rotate(
            new Point(rectangle.left().doubleValue(), rectangle.bottom().doubleValue()),
            center,
            radians));
  }

  private Point rotate(Point point, Point center, double radians) {
    double x = point.x() - center.x();
    double y = point.y() - center.y();
    return new Point(
        center.x() + x * Math.cos(radians) - y * Math.sin(radians),
        center.y() + x * Math.sin(radians) + y * Math.cos(radians));
  }

  private Point inverseRotate(Point point, Point center, double degrees) {
    return rotate(point, center, Math.toRadians(-degrees));
  }

  private Point center(MapRectangle rectangle) {
    return point(rectangle.center());
  }

  private Point point(MapPoint point) {
    return new Point(point.x().doubleValue(), point.y().doubleValue());
  }

  private double degrees(RotationDegrees rotation) {
    return rotation.value().doubleValue();
  }

  private record Point(double x, double y) {}

  private record Projection(double minimum, double maximum) {}

  record Bounds(double minimumX, double minimumY, double maximumX, double maximumY) {}
}
