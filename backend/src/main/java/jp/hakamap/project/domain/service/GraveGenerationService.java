package jp.hakamap.project.domain.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import jp.hakamap.project.domain.model.Grave;
import jp.hakamap.project.domain.result.ProjectInvariantException;
import jp.hakamap.project.domain.value.GraveId;
import jp.hakamap.project.domain.value.MapPoint;
import jp.hakamap.project.domain.value.MapRectangle;
import jp.hakamap.project.domain.value.MapSize;
import jp.hakamap.project.domain.value.RotationDegrees;

public final class GraveGenerationService {
  public List<Grave> matrix(
      int rows,
      int columns,
      MapPoint start,
      MapSize graveSize,
      BigDecimal horizontalGap,
      BigDecimal verticalGap,
      Instant commandTime,
      UuidSource uuidSource) {
    requirePositive(rows, columns);
    BigDecimal normalizedHorizontalGap = nonNegative(horizontalGap);
    BigDecimal normalizedVerticalGap = nonNegative(verticalGap);
    List<Grave> result = new ArrayList<>(Math.multiplyExact(rows, columns));
    BigDecimal stepX = graveSize.width().add(normalizedHorizontalGap);
    BigDecimal stepY = graveSize.height().add(normalizedVerticalGap);
    for (int row = 0; row < rows; row++) {
      for (int column = 0; column < columns; column++) {
        result.add(
            newGrave(
                new MapPoint(
                    start.x().add(stepX.multiply(BigDecimal.valueOf(column))),
                    start.y().add(stepY.multiply(BigDecimal.valueOf(row)))),
                graveSize,
                commandTime,
                uuidSource));
      }
    }
    return List.copyOf(result);
  }

  public List<Grave> fill(
      MapRectangle range,
      MapSize graveSize,
      BigDecimal horizontalGap,
      BigDecimal verticalGap,
      Instant commandTime,
      UuidSource uuidSource) {
    BigDecimal stepX = graveSize.width().add(nonNegative(horizontalGap));
    BigDecimal stepY = graveSize.height().add(nonNegative(verticalGap));
    List<Grave> result = new ArrayList<>();
    for (BigDecimal y = range.top();
        y.add(graveSize.height()).compareTo(range.bottom()) <= 0;
        y = y.add(stepY)) {
      for (BigDecimal x = range.left();
          x.add(graveSize.width()).compareTo(range.right()) <= 0;
          x = x.add(stepX)) {
        result.add(newGrave(new MapPoint(x, y), graveSize, commandTime, uuidSource));
      }
    }
    if (result.isEmpty()) {
      throw new ProjectInvariantException("no-graves-generated");
    }
    return List.copyOf(result);
  }

  public List<Grave> copies(
      List<Grave> sources,
      BigDecimal deltaX,
      BigDecimal deltaY,
      Instant commandTime,
      UuidSource uuidSource) {
    if (sources.isEmpty()) {
      throw new ProjectInvariantException("grave-selection-required");
    }
    return sources.stream()
        .map(
            source ->
                new Grave(
                    new GraveId(uuidSource.next()),
                    java.util.Optional.empty(),
                    java.util.Optional.empty(),
                    java.util.Optional.empty(),
                    source.rectangle().translate(deltaX, deltaY),
                    source.rotation(),
                    commandTime))
        .toList();
  }

  private Grave newGrave(MapPoint point, MapSize size, Instant commandTime, UuidSource uuidSource) {
    return new Grave(
        new GraveId(uuidSource.next()),
        java.util.Optional.empty(),
        java.util.Optional.empty(),
        java.util.Optional.empty(),
        new MapRectangle(point, size),
        RotationDegrees.ZERO,
        commandTime);
  }

  private void requirePositive(int rows, int columns) {
    if (rows <= 0 || columns <= 0) {
      throw new ProjectInvariantException("invalid-generation-count");
    }
  }

  private BigDecimal nonNegative(BigDecimal value) {
    MapPoint normalized = new MapPoint(value, BigDecimal.ZERO);
    if (normalized.x().signum() < 0) {
      throw new ProjectInvariantException("invalid-generation-gap");
    }
    return normalized.x();
  }
}
