package jp.hakamap.project.application.editing;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public final class CommandPayloads {
  private CommandPayloads() {}

  public sealed interface CommandPayload
      permits RenameProject,
          SetBackground,
          TransformBackground,
          RemoveBackground,
          CreateArea,
          UpdateArea,
          DeleteArea,
          CreateGrave,
          CreateGraveGrid,
          FillGraveRange,
          UpdateGraveInfo,
          MoveGraves,
          ResizeGrave,
          CopyGraves,
          DeleteGraves,
          NumberGraves,
          CreatePerson,
          UpdatePerson,
          DeletePerson,
          AddAttachments,
          UpdateAttachment,
          ReorderAttachments,
          DeleteAttachment {}

  public record RenameProject(String name) implements CommandPayload {}

  public record SetBackground(
      UUID fileSelectionId,
      BigDecimal x,
      BigDecimal y,
      BigDecimal rotation,
      BigDecimal scaleX,
      BigDecimal scaleY)
      implements CommandPayload {}

  public record TransformBackground(
      BigDecimal x, BigDecimal y, BigDecimal rotation, BigDecimal scaleX, BigDecimal scaleY)
      implements CommandPayload {}

  public record RemoveBackground() implements CommandPayload {}

  public record CreateArea(
      String clientRef,
      String name,
      BigDecimal x,
      BigDecimal y,
      BigDecimal width,
      BigDecimal height,
      String colorPreset,
      boolean visible)
      implements CommandPayload {}

  public record UpdateArea(
      UUID areaId,
      String name,
      BigDecimal x,
      BigDecimal y,
      BigDecimal width,
      BigDecimal height,
      BigDecimal rotation,
      String colorPreset,
      boolean visible)
      implements CommandPayload {}

  public record DeleteArea(UUID areaId) implements CommandPayload {}

  public record CreateGrave(
      String clientRef, BigDecimal x, BigDecimal y, BigDecimal width, BigDecimal height)
      implements CommandPayload {}

  public record CreateGraveGrid(
      String clientRefPrefix,
      BigDecimal x,
      BigDecimal y,
      int rows,
      int columns,
      BigDecimal graveWidth,
      BigDecimal graveHeight,
      BigDecimal horizontalGap,
      BigDecimal verticalGap)
      implements CommandPayload {}

  public record FillGraveRange(
      String clientRefPrefix,
      BigDecimal rangeX,
      BigDecimal rangeY,
      BigDecimal rangeWidth,
      BigDecimal rangeHeight,
      BigDecimal graveWidth,
      BigDecimal graveHeight,
      BigDecimal horizontalGap,
      BigDecimal verticalGap)
      implements CommandPayload {}

  public record UpdateGraveInfo(UUID graveId, String managementNumber, String name, String notes)
      implements CommandPayload {}

  public record MoveGraves(List<UUID> graveIds, BigDecimal deltaX, BigDecimal deltaY)
      implements CommandPayload {
    public MoveGraves {
      graveIds = List.copyOf(graveIds);
    }
  }

  public record ResizeGrave(
      UUID graveId,
      BigDecimal x,
      BigDecimal y,
      BigDecimal width,
      BigDecimal height,
      BigDecimal rotation)
      implements CommandPayload {}

  public record CopyGraves(List<UUID> graveIds, BigDecimal deltaX, BigDecimal deltaY)
      implements CommandPayload {
    public CopyGraves {
      graveIds = List.copyOf(graveIds);
    }
  }

  public record DeleteGraves(List<UUID> graveIds) implements CommandPayload {
    public DeleteGraves {
      graveIds = List.copyOf(graveIds);
    }
  }

  public record NumberGraves(String numberingPreviewToken) implements CommandPayload {}

  public record CreatePerson(UUID graveId, String clientRef, String name, String posthumousName)
      implements CommandPayload {}

  public record UpdatePerson(UUID personId, String name, String posthumousName)
      implements CommandPayload {}

  public record DeletePerson(UUID personId) implements CommandPayload {}

  public record AddAttachments(UUID graveId, List<UUID> fileSelectionIds)
      implements CommandPayload {
    public AddAttachments {
      fileSelectionIds = List.copyOf(fileSelectionIds);
    }
  }

  public record UpdateAttachment(UUID assetId, String displayName, String description)
      implements CommandPayload {}

  public record ReorderAttachments(UUID graveId, List<UUID> orderedAssetIds)
      implements CommandPayload {
    public ReorderAttachments {
      orderedAssetIds = List.copyOf(orderedAssetIds);
    }
  }

  public record DeleteAttachment(UUID assetId) implements CommandPayload {}
}
