package jp.hakamap.project.domain.model;

import java.util.Objects;
import jp.hakamap.project.domain.value.AreaColorPreset;
import jp.hakamap.project.domain.value.AreaId;
import jp.hakamap.project.domain.value.AreaName;
import jp.hakamap.project.domain.value.DisplayOrder;
import jp.hakamap.project.domain.value.MapRectangle;
import jp.hakamap.project.domain.value.RotationDegrees;

public record Area(
    AreaId id,
    AreaName name,
    MapRectangle rectangle,
    RotationDegrees rotation,
    AreaColorPreset color,
    boolean visible,
    DisplayOrder displayOrder) {
  public Area {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(rectangle, "rectangle");
    Objects.requireNonNull(rotation, "rotation");
    Objects.requireNonNull(color, "color");
    Objects.requireNonNull(displayOrder, "displayOrder");
  }

  public Area(
      AreaId id,
      AreaName name,
      MapRectangle rectangle,
      AreaColorPreset color,
      boolean visible,
      DisplayOrder displayOrder) {
    this(id, name, rectangle, RotationDegrees.ZERO, color, visible, displayOrder);
  }

  public Area withDisplayOrder(DisplayOrder order) {
    return new Area(id, name, rectangle, rotation, color, visible, order);
  }
}
