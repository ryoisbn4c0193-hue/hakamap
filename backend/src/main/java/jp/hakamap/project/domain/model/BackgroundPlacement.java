package jp.hakamap.project.domain.model;

import java.util.Objects;
import jp.hakamap.project.domain.value.AssetId;
import jp.hakamap.project.domain.value.BackgroundScale;
import jp.hakamap.project.domain.value.MapPoint;
import jp.hakamap.project.domain.value.RotationDegrees;

public record BackgroundPlacement(
    AssetId assetId,
    MapPoint position,
    RotationDegrees rotation,
    BackgroundScale scaleX,
    BackgroundScale scaleY) {
  public BackgroundPlacement {
    Objects.requireNonNull(assetId, "assetId");
    Objects.requireNonNull(position, "position");
    Objects.requireNonNull(rotation, "rotation");
    Objects.requireNonNull(scaleX, "scaleX");
    Objects.requireNonNull(scaleY, "scaleY");
  }
}
