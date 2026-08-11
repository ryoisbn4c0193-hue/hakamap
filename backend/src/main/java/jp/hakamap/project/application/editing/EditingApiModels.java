package jp.hakamap.project.application.editing;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class EditingApiModels {
  private EditingApiModels() {}

  public record ProjectSnapshotResponse(
      UUID projectId,
      long revision,
      boolean dirty,
      ProjectResponse project,
      BackgroundResponse background,
      List<AreaResponse> areas,
      List<GraveResponse> graves,
      List<AssetResponse> assets,
      List<GraveStateResponse> graveStates,
      HistorySummaryResponse historySummary,
      CapabilitiesResponse capabilities) {}

  public record ProjectResponse(
      UUID projectId, String name, Instant createdAt, Instant updatedAt) {}

  public record BackgroundResponse(
      UUID assetId,
      BigDecimal x,
      BigDecimal y,
      BigDecimal rotation,
      BigDecimal scaleX,
      BigDecimal scaleY) {}

  public record AreaResponse(
      UUID areaId,
      String name,
      BigDecimal x,
      BigDecimal y,
      BigDecimal width,
      BigDecimal height,
      BigDecimal rotation,
      String colorPreset,
      boolean visible,
      int displayOrder) {}

  public record GraveResponse(
      UUID graveId,
      String managementNumber,
      String name,
      String notes,
      BigDecimal x,
      BigDecimal y,
      BigDecimal width,
      BigDecimal height,
      BigDecimal rotation,
      Instant updatedAt) {}

  public record AssetResponse(
      UUID assetId,
      String assetType,
      UUID graveId,
      String displayName,
      String description,
      String mediaType,
      long sizeBytes,
      Instant createdAt,
      Instant updatedAt,
      Integer displayOrder) {}

  public record GraveStateResponse(
      UUID graveId,
      UUID areaId,
      String completionStatus,
      List<String> incompleteReasons,
      List<String> warnings) {}

  public record HistorySummaryResponse(
      boolean canUndo, boolean canRedo, int undoCount, int redoCount) {}

  public record CapabilitiesResponse(
      boolean canSave, boolean canUndo, boolean canRedo, boolean canEdit) {}

  public record CommandResponse(
      String status,
      long revision,
      boolean dirty,
      List<AreaResponse> upsertedAreas,
      List<UUID> deletedAreaIds,
      List<GraveResponse> upsertedGraves,
      List<UUID> deletedGraveIds,
      List<PersonChangeResponse> personChanges,
      List<AssetResponse> upsertedAssets,
      List<UUID> deletedAssetIds,
      List<GraveStateResponse> graveStates,
      List<WarningResponse> warnings,
      HistorySummaryResponse historySummary,
      CommandResultResponse result) {}

  public record PersonChangeResponse(
      UUID graveId,
      List<PersonResponse> upsertedPeople,
      List<UUID> deletedPersonIds,
      int totalCount) {}

  public record PersonResponse(
      UUID personId,
      UUID graveId,
      String name,
      String posthumousName,
      Instant createdAt,
      Instant updatedAt,
      int displayOrder) {}

  public record WarningResponse(String code, int count) {}

  public record CommandResultResponse(
      List<ClientReferenceResponse> createdEntities,
      List<NumberingResultResponse> numberingResults) {}

  public record ClientReferenceResponse(String clientRef, UUID entityId) {}

  public record NumberingResultResponse(UUID graveId, String managementNumber) {}

  public record HistoryResponse(
      UUID projectId,
      long revision,
      boolean dirty,
      List<HistoryItemResponse> items,
      HistorySummaryResponse historySummary) {}

  public record HistoryItemResponse(
      UUID commandId,
      String commandType,
      Instant commandTimestamp,
      int targetCount,
      boolean applied,
      boolean savedMarker) {}

  public record GravePeoplePageResponse(
      UUID projectId,
      UUID graveId,
      long revision,
      List<PersonResponse> items,
      String nextCursor,
      int totalCount) {}

  public record GraveSearchPageResponse(
      UUID projectId,
      long revision,
      List<GraveSearchResultResponse> items,
      String nextCursor,
      int totalCount) {}

  public record GraveSearchResultResponse(
      UUID graveId, String areaName, String managementNumber, String graveName) {}

  public record NumberingPreviewResponse(
      long revision,
      String numberingPreviewToken,
      Instant expiresAt,
      List<NumberingResultResponse> assignments) {}

  public record ConfirmationRequiredResponse(
      String status,
      long revision,
      String confirmationToken,
      Instant expiresAt,
      List<WarningResponse> warnings) {}
}
