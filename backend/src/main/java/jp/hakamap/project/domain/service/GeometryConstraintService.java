package jp.hakamap.project.domain.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import jp.hakamap.project.domain.result.ProjectInvariantException;
import jp.hakamap.project.domain.value.MapRectangle;

public final class GeometryConstraintService {
  public <T> void requireNoAreaOverlap(
      Collection<T> entities, Function<T, MapRectangle> rectangle, String code) {
    List<T> values = new ArrayList<>(entities);
    for (int first = 0; first < values.size(); first++) {
      for (int second = first + 1; second < values.size(); second++) {
        if (rectangle.apply(values.get(first)).overlapsArea(rectangle.apply(values.get(second)))) {
          throw new ProjectInvariantException(code);
        }
      }
    }
  }
}
