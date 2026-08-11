package jp.hakamap.project.domain.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import jp.hakamap.project.domain.result.ProjectInvariantException;
import jp.hakamap.project.domain.value.MapRectangle;
import jp.hakamap.project.domain.value.RotationDegrees;

public final class GeometryConstraintService {
  public <T> void requireNoAreaOverlap(
      Collection<T> entities, Function<T, MapRectangle> rectangle, String code) {
    requireNoAreaOverlap(entities, rectangle, ignored -> RotationDegrees.ZERO, code);
  }

  public <T> void requireNoAreaOverlap(
      Collection<T> entities,
      Function<T, MapRectangle> rectangle,
      Function<T, RotationDegrees> rotation,
      String code) {
    List<T> values = new ArrayList<>(entities);
    RotatedRectangleGeometry geometry = new RotatedRectangleGeometry();
    for (int first = 0; first < values.size(); first++) {
      for (int second = first + 1; second < values.size(); second++) {
        T firstValue = values.get(first);
        T secondValue = values.get(second);
        if (geometry.overlapsArea(
            rectangle.apply(firstValue),
            rotation.apply(firstValue),
            rectangle.apply(secondValue),
            rotation.apply(secondValue))) {
          throw new ProjectInvariantException(code);
        }
      }
    }
  }
}
