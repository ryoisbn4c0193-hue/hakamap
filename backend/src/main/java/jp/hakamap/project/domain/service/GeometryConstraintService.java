package jp.hakamap.project.domain.service;

import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import jp.hakamap.project.domain.result.ProjectInvariantException;
import jp.hakamap.project.domain.value.MapRectangle;
import jp.hakamap.project.domain.value.RotationDegrees;

public final class GeometryConstraintService {
  private static final double BOUNDS_EPSILON = 0.000_000_1;

  public <T> void requireNoAreaOverlap(
      Collection<T> entities, Function<T, MapRectangle> rectangle, String code) {
    requireNoAreaOverlap(entities, rectangle, ignored -> RotationDegrees.ZERO, code);
  }

  public <T> void requireNoAreaOverlap(
      Collection<T> entities,
      Function<T, MapRectangle> rectangle,
      Function<T, RotationDegrees> rotation,
      String code) {
    RotatedRectangleGeometry geometry = new RotatedRectangleGeometry();
    List<GeometryCandidate> values =
        entities.stream()
            .map(
                entity -> {
                  MapRectangle candidateRectangle = rectangle.apply(entity);
                  RotationDegrees candidateRotation = rotation.apply(entity);
                  return new GeometryCandidate(
                      candidateRectangle,
                      candidateRotation,
                      geometry.bounds(candidateRectangle, candidateRotation));
                })
            .sorted(
                java.util.Comparator.comparingDouble(candidate -> candidate.bounds().minimumX()))
            .toList();
    for (int first = 0; first < values.size(); first++) {
      GeometryCandidate firstValue = values.get(first);
      for (int second = first + 1; second < values.size(); second++) {
        GeometryCandidate secondValue = values.get(second);
        if (secondValue.bounds().minimumX() > firstValue.bounds().maximumX() + BOUNDS_EPSILON) {
          break;
        }
        if (secondValue.bounds().minimumY() > firstValue.bounds().maximumY() + BOUNDS_EPSILON
            || firstValue.bounds().minimumY() > secondValue.bounds().maximumY() + BOUNDS_EPSILON) {
          continue;
        }
        if (geometry.overlapsArea(
            firstValue.rectangle(),
            firstValue.rotation(),
            secondValue.rectangle(),
            secondValue.rotation())) {
          throw new ProjectInvariantException(code);
        }
      }
    }
  }

  private record GeometryCandidate(
      MapRectangle rectangle, RotationDegrees rotation, RotatedRectangleGeometry.Bounds bounds) {}
}
