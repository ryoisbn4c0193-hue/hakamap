package jp.hakamap.project.application.editing;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import jp.hakamap.infrastructure.http.LocalApiSecurityFilter;
import jp.hakamap.project.application.history.CommandType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@RestController
@RequestMapping("/api/v1/projects/{projectId}")
public class ProjectEditingController {
  private static final Map<CommandType, Class<? extends CommandPayloads.CommandPayload>>
      PAYLOAD_TYPES =
          Map.ofEntries(
              entry(CommandType.RENAME_PROJECT, CommandPayloads.RenameProject.class),
              entry(CommandType.SET_BACKGROUND, CommandPayloads.SetBackground.class),
              entry(CommandType.TRANSFORM_BACKGROUND, CommandPayloads.TransformBackground.class),
              entry(CommandType.REMOVE_BACKGROUND, CommandPayloads.RemoveBackground.class),
              entry(CommandType.CREATE_AREA, CommandPayloads.CreateArea.class),
              entry(CommandType.UPDATE_AREA, CommandPayloads.UpdateArea.class),
              entry(CommandType.DELETE_AREA, CommandPayloads.DeleteArea.class),
              entry(CommandType.CREATE_GRAVE, CommandPayloads.CreateGrave.class),
              entry(CommandType.CREATE_GRAVE_GRID, CommandPayloads.CreateGraveGrid.class),
              entry(CommandType.FILL_GRAVE_RANGE, CommandPayloads.FillGraveRange.class),
              entry(CommandType.UPDATE_GRAVE_INFO, CommandPayloads.UpdateGraveInfo.class),
              entry(CommandType.MOVE_GRAVES, CommandPayloads.MoveGraves.class),
              entry(CommandType.RESIZE_GRAVE, CommandPayloads.ResizeGrave.class),
              entry(CommandType.COPY_GRAVES, CommandPayloads.CopyGraves.class),
              entry(CommandType.DELETE_GRAVES, CommandPayloads.DeleteGraves.class),
              entry(CommandType.NUMBER_GRAVES, CommandPayloads.NumberGraves.class),
              entry(CommandType.CREATE_PERSON, CommandPayloads.CreatePerson.class),
              entry(CommandType.UPDATE_PERSON, CommandPayloads.UpdatePerson.class),
              entry(CommandType.DELETE_PERSON, CommandPayloads.DeletePerson.class),
              entry(CommandType.ADD_ATTACHMENTS, CommandPayloads.AddAttachments.class),
              entry(CommandType.UPDATE_ATTACHMENT, CommandPayloads.UpdateAttachment.class),
              entry(CommandType.REORDER_ATTACHMENTS, CommandPayloads.ReorderAttachments.class),
              entry(CommandType.DELETE_ATTACHMENT, CommandPayloads.DeleteAttachment.class));

  private final ProjectEditingApiService editing;

  private final JsonMapper json;

  private final BackgroundTileService backgroundTiles;

  public ProjectEditingController(ProjectEditingApiService editing, JsonMapper json) {
    this(editing, json, null);
  }

  @Autowired
  public ProjectEditingController(
      ProjectEditingApiService editing, JsonMapper json, BackgroundTileService backgroundTiles) {
    this.editing = editing;
    this.backgroundTiles = backgroundTiles;
    this.json =
        json.rebuild()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .build();
  }

  @GetMapping("/background/tiles/manifest")
  BackgroundTileService.TileManifest backgroundManifest(@PathVariable UUID projectId) {
    requireBackgroundTiles();
    EditingApiModels.BackgroundResponse background = editing.snapshot(projectId).background();
    if (background == null) {
      throw new EditingApiException("asset-not-found");
    }
    return backgroundTiles.manifest(editing.assetContent(projectId, background.assetId()));
  }

  @GetMapping("/background/tiles/{level}/{column}/{row}.png")
  ResponseEntity<byte[]> backgroundTile(
      @PathVariable UUID projectId,
      @PathVariable int level,
      @PathVariable int column,
      @PathVariable int row) {
    requireBackgroundTiles();
    EditingApiModels.BackgroundResponse background = editing.snapshot(projectId).background();
    if (background == null) {
      throw new EditingApiException("asset-not-found");
    }
    BackgroundTileService.TileContent tile =
        backgroundTiles.tile(
            editing.assetContent(projectId, background.assetId()), level, column, row);
    try {
      byte[] bytes = Files.readAllBytes(tile.path());
      return ResponseEntity.ok()
          .contentType(MediaType.IMAGE_PNG)
          .contentLength(tile.sizeBytes())
          .header("X-Content-Type-Options", "nosniff")
          .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600")
          .body(bytes);
    } catch (IOException exception) {
      throw new EditingApiException("background-tile-not-found");
    }
  }

  private void requireBackgroundTiles() {
    if (backgroundTiles == null) {
      throw new EditingApiException("background-tile-invalid");
    }
  }

  @GetMapping("/snapshot")
  EditingApiModels.ProjectSnapshotResponse snapshot(@PathVariable UUID projectId) {
    return editing.snapshot(projectId);
  }

  @PostMapping("/commands")
  Object command(
      @PathVariable UUID projectId, @RequestBody CommandEnvelope body, HttpServletRequest request) {
    CommandType type = commandType(body.commandType());
    CommandPayloads.CommandPayload payload = deserializePayload(type, body.payload());
    return editing.execute(projectId, sessionId(request), body.expectedRevision(), type, payload);
  }

  CommandPayloads.CommandPayload deserializePayload(CommandType type, JsonNode payloadNode) {
    Class<? extends CommandPayloads.CommandPayload> payloadType = PAYLOAD_TYPES.get(type);
    if (payloadType == null) {
      throw new EditingApiException("request-unknown-command");
    }
    try {
      return json.treeToValue(payloadNode, payloadType);
    } catch (RuntimeException exception) {
      throw new EditingApiException("request-field-invalid");
    }
  }

  @PostMapping("/command-confirmations/{confirmationToken}")
  EditingApiModels.CommandResponse confirm(
      @PathVariable UUID projectId,
      @PathVariable String confirmationToken,
      @RequestBody RevisionRequest body,
      HttpServletRequest request) {
    return editing.confirm(
        projectId, sessionId(request), confirmationToken, body.expectedRevision());
  }

  @DeleteMapping("/command-confirmations/{confirmationToken}")
  ResponseEntity<Void> cancelConfirmation(
      @PathVariable String confirmationToken, HttpServletRequest request) {
    editing.cancelConfirmation(sessionId(request), confirmationToken);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/numbering-previews")
  EditingApiModels.NumberingPreviewResponse numberingPreview(
      @PathVariable UUID projectId,
      @RequestBody NumberingPreviewRequest body,
      HttpServletRequest request) {
    return editing.previewNumbering(
        projectId,
        sessionId(request),
        body.expectedRevision(),
        body.graveIds(),
        body.prefix(),
        body.startNumber(),
        body.digitCount(),
        body.suffix());
  }

  @PostMapping("/history/undo")
  EditingApiModels.CommandResponse undo(
      @PathVariable UUID projectId, @RequestBody RevisionRequest body) {
    return editing.undo(projectId, body.expectedRevision());
  }

  @PostMapping("/history/redo")
  EditingApiModels.CommandResponse redo(
      @PathVariable UUID projectId, @RequestBody RevisionRequest body) {
    return editing.redo(projectId, body.expectedRevision());
  }

  @GetMapping("/history")
  EditingApiModels.HistoryResponse history(@PathVariable UUID projectId) {
    return editing.history(projectId);
  }

  @GetMapping("/graves/{graveId}/people")
  EditingApiModels.GravePeoplePageResponse people(
      @PathVariable UUID projectId,
      @PathVariable UUID graveId,
      @RequestParam(required = false) String cursor,
      HttpServletRequest request) {
    return editing.people(projectId, graveId, cursor, sessionId(request));
  }

  @GetMapping("/search")
  EditingApiModels.GraveSearchPageResponse search(
      @PathVariable UUID projectId,
      @RequestParam String q,
      @RequestParam(required = false) String cursor,
      HttpServletRequest request) {
    return editing.search(projectId, q, cursor, sessionId(request));
  }

  @GetMapping("/assets/{assetId}/content")
  ResponseEntity<byte[]> assetContent(@PathVariable UUID projectId, @PathVariable UUID assetId) {
    return assetResponse(editing.assetContent(projectId, assetId));
  }

  @GetMapping("/assets/{assetId}/thumbnail")
  ResponseEntity<byte[]> assetThumbnail(@PathVariable UUID projectId, @PathVariable UUID assetId) {
    return assetResponse(editing.assetContent(projectId, assetId));
  }

  private ResponseEntity<byte[]> assetResponse(ProjectEditingApiService.AssetContent content) {
    try {
      byte[] bytes = Files.readAllBytes(content.path());
      if (bytes.length != content.sizeBytes()) {
        throw new EditingApiException("asset-integrity-invalid");
      }
      return ResponseEntity.ok()
          .contentType(MediaType.parseMediaType(content.mediaType()))
          .contentLength(bytes.length)
          .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"hakamap-image\"")
          .header("X-Content-Type-Options", "nosniff")
          .header("Content-Security-Policy", "default-src 'none'; img-src 'self'; sandbox")
          .header(HttpHeaders.CACHE_CONTROL, "no-store")
          .body(bytes);
    } catch (IOException exception) {
      throw new EditingApiException("asset-integrity-invalid");
    }
  }

  CommandType commandType(String apiValue) {
    if (apiValue == null || !apiValue.matches("^[a-z][A-Za-z]*$")) {
      throw new EditingApiException("request-unknown-command");
    }
    String enumName = apiValue.replaceAll("([A-Z])", "_$1").toUpperCase(Locale.ROOT);
    try {
      return CommandType.valueOf(enumName);
    } catch (IllegalArgumentException exception) {
      throw new EditingApiException("request-unknown-command");
    }
  }

  private String sessionId(HttpServletRequest request) {
    return (String) request.getAttribute(LocalApiSecurityFilter.AUTHENTICATED_SESSION_ATTRIBUTE);
  }

  private static Map.Entry<CommandType, Class<? extends CommandPayloads.CommandPayload>> entry(
      CommandType type, Class<? extends CommandPayloads.CommandPayload> payload) {
    return Map.entry(type, payload);
  }

  public record CommandEnvelope(long expectedRevision, String commandType, JsonNode payload) {}

  public record RevisionRequest(long expectedRevision) {}

  public record NumberingPreviewRequest(
      long expectedRevision,
      List<UUID> graveIds,
      String prefix,
      BigInteger startNumber,
      int digitCount,
      String suffix) {
    public NumberingPreviewRequest {
      graveIds = List.copyOf(graveIds);
    }
  }
}
