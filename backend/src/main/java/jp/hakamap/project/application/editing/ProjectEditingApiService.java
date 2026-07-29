package jp.hakamap.project.application.editing;

import static jp.hakamap.project.application.editing.EditingApiModels.AreaResponse;
import static jp.hakamap.project.application.editing.EditingApiModels.AssetResponse;
import static jp.hakamap.project.application.editing.EditingApiModels.BackgroundResponse;
import static jp.hakamap.project.application.editing.EditingApiModels.CapabilitiesResponse;
import static jp.hakamap.project.application.editing.EditingApiModels.ClientReferenceResponse;
import static jp.hakamap.project.application.editing.EditingApiModels.CommandResponse;
import static jp.hakamap.project.application.editing.EditingApiModels.CommandResultResponse;
import static jp.hakamap.project.application.editing.EditingApiModels.GravePeoplePageResponse;
import static jp.hakamap.project.application.editing.EditingApiModels.GraveResponse;
import static jp.hakamap.project.application.editing.EditingApiModels.GraveStateResponse;
import static jp.hakamap.project.application.editing.EditingApiModels.HistoryItemResponse;
import static jp.hakamap.project.application.editing.EditingApiModels.HistoryResponse;
import static jp.hakamap.project.application.editing.EditingApiModels.HistorySummaryResponse;
import static jp.hakamap.project.application.editing.EditingApiModels.NumberingPreviewResponse;
import static jp.hakamap.project.application.editing.EditingApiModels.NumberingResultResponse;
import static jp.hakamap.project.application.editing.EditingApiModels.PersonChangeResponse;
import static jp.hakamap.project.application.editing.EditingApiModels.PersonResponse;
import static jp.hakamap.project.application.editing.EditingApiModels.ProjectResponse;
import static jp.hakamap.project.application.editing.EditingApiModels.ProjectSnapshotResponse;
import static jp.hakamap.project.application.editing.EditingApiModels.WarningResponse;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import jp.hakamap.infrastructure.fileselection.FileSelectionPurpose;
import jp.hakamap.infrastructure.fileselection.FileSelectionService;
import jp.hakamap.project.application.catalog.OpenProjectManager;
import jp.hakamap.project.application.history.CommandType;
import jp.hakamap.project.application.history.EntityDelta;
import jp.hakamap.project.application.history.ProjectChangeSet;
import jp.hakamap.project.application.history.ProjectEditingSession;
import jp.hakamap.project.application.history.ProjectFingerprintCalculator;
import jp.hakamap.project.domain.model.Area;
import jp.hakamap.project.domain.model.AssetMetadata;
import jp.hakamap.project.domain.model.BackgroundPlacement;
import jp.hakamap.project.domain.model.Grave;
import jp.hakamap.project.domain.model.GraveStatus;
import jp.hakamap.project.domain.model.Person;
import jp.hakamap.project.domain.model.ProjectAggregate;
import jp.hakamap.project.domain.model.ProjectMetadata;
import jp.hakamap.project.domain.result.ProjectInvariantException;
import jp.hakamap.project.domain.service.GraveGenerationService;
import jp.hakamap.project.domain.service.NumberingAssignment;
import jp.hakamap.project.domain.service.NumberingRequest;
import jp.hakamap.project.domain.service.NumberingService;
import jp.hakamap.project.domain.service.UuidSource;
import jp.hakamap.project.domain.value.AreaColorPreset;
import jp.hakamap.project.domain.value.AreaId;
import jp.hakamap.project.domain.value.AreaName;
import jp.hakamap.project.domain.value.AssetDescription;
import jp.hakamap.project.domain.value.AssetDisplayName;
import jp.hakamap.project.domain.value.AssetId;
import jp.hakamap.project.domain.value.AssetType;
import jp.hakamap.project.domain.value.BackgroundScale;
import jp.hakamap.project.domain.value.DisplayOrder;
import jp.hakamap.project.domain.value.DomainWarningCode;
import jp.hakamap.project.domain.value.GraveId;
import jp.hakamap.project.domain.value.GraveName;
import jp.hakamap.project.domain.value.GraveNotes;
import jp.hakamap.project.domain.value.ManagementNumber;
import jp.hakamap.project.domain.value.MapPoint;
import jp.hakamap.project.domain.value.MapRectangle;
import jp.hakamap.project.domain.value.MapSize;
import jp.hakamap.project.domain.value.PersonId;
import jp.hakamap.project.domain.value.PersonName;
import jp.hakamap.project.domain.value.PosthumousName;
import jp.hakamap.project.domain.value.ProjectName;
import jp.hakamap.project.domain.value.RotationDegrees;

public final class ProjectEditingApiService {
  private static final int PEOPLE_PAGE_SIZE = 100;

  private final OpenProjectManager openProjects;

  private final ProjectFingerprintCalculator fingerprints;

  private final Clock clock;

  private final UuidSource uuids;

  private final FileSelectionService fileSelections;

  private final ProjectDeltaFactory deltas = new ProjectDeltaFactory();

  private final GraveGenerationService graveGeneration = new GraveGenerationService();

  private final NumberingService numbering = new NumberingService();

  private final EditingTokenStore tokens;

  public ProjectEditingApiService(
      OpenProjectManager openProjects,
      ProjectFingerprintCalculator fingerprints,
      Clock clock,
      UuidSource uuids,
      FileSelectionService fileSelections) {
    this.openProjects = openProjects;
    this.fingerprints = fingerprints;
    this.clock = clock;
    this.uuids = uuids;
    this.fileSelections = fileSelections;
    this.tokens = new EditingTokenStore(clock);
  }

  public synchronized ProjectSnapshotResponse snapshot(UUID projectId) {
    ProjectEditingSession session = session(projectId);
    ProjectAggregate project = session.current();
    return new ProjectSnapshotResponse(
        projectId,
        session.revision(),
        session.dirty(),
        toProject(project),
        project.background().map(this::toBackground).orElse(null),
        project.areas().values().stream()
            .sorted(Comparator.comparing(Area::displayOrder))
            .map(this::toArea)
            .toList(),
        project.graves().values().stream()
            .sorted(Comparator.comparing(grave -> grave.id().value()))
            .map(this::toGrave)
            .toList(),
        project.assets().values().stream()
            .sorted(Comparator.comparing(asset -> asset.id().value()))
            .map(this::toAsset)
            .toList(),
        allGraveStates(project),
        historySummary(session),
        capabilities(session));
  }

  public synchronized Object execute(
      UUID projectId,
      String sessionId,
      long expectedRevision,
      CommandType commandType,
      CommandPayloads.CommandPayload payload) {
    ProjectEditingSession session = session(projectId);
    session.requireRevision(expectedRevision);
    ProjectAggregate before = session.current();
    Instant timestamp = clock.instant();
    Mutation mutation = mutate(before, commandType, payload, timestamp, sessionId, projectId);
    if (fingerprints.calculate(before).equals(fingerprints.calculate(mutation.candidate()))) {
      return emptyResponse(session, "noChange");
    }
    ProjectChangeSet changeSet =
        deltas.between(before, mutation.candidate(), commandType, timestamp, uuids.next());
    List<WarningResponse> newWarnings = newPlacementWarnings(before, mutation.candidate());
    if (!newWarnings.isEmpty()) {
      EditingTokenStore.StoredConfirmation stored =
          tokens.storeConfirmation(sessionId, projectId, expectedRevision, changeSet);
      return new EditingApiModels.ConfirmationRequiredResponse(
          "confirmationRequired",
          expectedRevision,
          stored.token(),
          stored.expiresAt(),
          newWarnings);
    }
    session.apply(expectedRevision, changeSet);
    return response(session, changeSet, mutation.result());
  }

  public synchronized CommandResponse confirm(
      UUID projectId, String sessionId, String token, long expectedRevision) {
    ProjectEditingSession session = session(projectId);
    session.requireRevision(expectedRevision);
    ProjectChangeSet changeSet =
        tokens.consumeConfirmation(token, sessionId, projectId, expectedRevision);
    session.apply(expectedRevision, changeSet);
    return response(session, changeSet, emptyResult());
  }

  public synchronized void cancelConfirmation(String sessionId, String token) {
    tokens.discardConfirmation(token, sessionId);
  }

  public synchronized NumberingPreviewResponse previewNumbering(
      UUID projectId,
      String sessionId,
      long expectedRevision,
      List<UUID> graveIds,
      String prefix,
      BigInteger startNumber,
      int digitCount,
      String suffix) {
    ProjectEditingSession session = session(projectId);
    session.requireRevision(expectedRevision);
    requireUniqueNonEmpty(graveIds);
    List<Grave> graves =
        graveIds.stream().map(id -> requireGrave(session.current(), new GraveId(id))).toList();
    for (Grave grave : graves) {
      if (session.current().graveStatus(grave.id()).areaId().isEmpty()) {
        throw new ProjectInvariantException("grave-unassigned-for-numbering");
      }
    }
    List<NumberingAssignment> assignments =
        numbering.preview(graves, new NumberingRequest(prefix, startNumber, digitCount, suffix));
    EditingTokenStore.StoredNumbering stored =
        tokens.storeNumbering(sessionId, projectId, expectedRevision, assignments);
    return new NumberingPreviewResponse(
        expectedRevision,
        stored.token(),
        stored.expiresAt(),
        assignments.stream()
            .map(
                assignment ->
                    new NumberingResultResponse(
                        assignment.graveId().value(), assignment.managementNumber().value()))
            .toList());
  }

  public synchronized CommandResponse undo(UUID projectId, long expectedRevision) {
    ProjectEditingSession session = session(projectId);
    ProjectAggregate before = session.current();
    session.undo(expectedRevision);
    return stateTransitionResponse(session, before, CommandType.RENAME_PROJECT);
  }

  public synchronized CommandResponse redo(UUID projectId, long expectedRevision) {
    ProjectEditingSession session = session(projectId);
    ProjectAggregate before = session.current();
    session.redo(expectedRevision);
    return stateTransitionResponse(session, before, CommandType.RENAME_PROJECT);
  }

  public synchronized HistoryResponse history(UUID projectId) {
    ProjectEditingSession session = session(projectId);
    return new HistoryResponse(
        projectId,
        session.revision(),
        session.dirty(),
        session.history().stream()
            .map(
                item ->
                    new HistoryItemResponse(
                        item.commandId().value(),
                        apiCommandType(item.commandType()),
                        item.commandTimestamp(),
                        item.targetCount(),
                        item.applied(),
                        item.savedMarker()))
            .toList(),
        historySummary(session));
  }

  public synchronized GravePeoplePageResponse people(
      UUID projectId, UUID graveUuid, String cursor, String httpSessionId) {
    ProjectEditingSession session = session(projectId);
    GraveId graveId = new GraveId(graveUuid);
    requireGrave(session.current(), graveId);
    int start =
        tokens.resolvePeopleCursor(cursor, httpSessionId, projectId, graveUuid, session.revision());
    List<Person> all =
        session.current().people().values().stream()
            .filter(person -> person.graveId().equals(graveId))
            .sorted(
                Comparator.comparing(Person::displayOrder)
                    .thenComparing(person -> person.id().value()))
            .toList();
    int end = Math.min(start + PEOPLE_PAGE_SIZE, all.size());
    List<PersonResponse> items = all.subList(start, end).stream().map(this::toPerson).toList();
    String next =
        end < all.size()
            ? tokens.storePeopleCursor(httpSessionId, projectId, graveUuid, session.revision(), end)
            : null;
    return new GravePeoplePageResponse(
        projectId, graveUuid, session.revision(), items, next, all.size());
  }

  public synchronized AssetContent assetContent(UUID projectId, UUID assetUuid) {
    ProjectAggregate project = session(projectId).current();
    AssetMetadata asset = project.assets().get(new AssetId(assetUuid));
    if (asset == null) {
      throw new EditingApiException("asset-not-found");
    }
    Path root = openProjects.projectRoot(projectId).toAbsolutePath().normalize();
    Path content = root.resolve(asset.relativePath()).normalize();
    if (!content.startsWith(root)
        || Files.isSymbolicLink(content)
        || !Files.isRegularFile(content)) {
      throw new EditingApiException("asset-not-found");
    }
    return new AssetContent(content, asset.storedMediaType(), asset.sizeBytes());
  }

  private Mutation mutate(
      ProjectAggregate before,
      CommandType type,
      CommandPayloads.CommandPayload payload,
      Instant timestamp,
      String httpSessionId,
      UUID projectId) {
    return switch (type) {
      case RENAME_PROJECT -> rename(before, require(payload, CommandPayloads.RenameProject.class));
      case SET_BACKGROUND ->
          setBackground(
              before,
              require(payload, CommandPayloads.SetBackground.class),
              timestamp,
              httpSessionId,
              projectId);
      case TRANSFORM_BACKGROUND ->
          transformBackground(before, require(payload, CommandPayloads.TransformBackground.class));
      case REMOVE_BACKGROUND ->
          removeBackground(before, require(payload, CommandPayloads.RemoveBackground.class));
      case CREATE_AREA -> createArea(before, require(payload, CommandPayloads.CreateArea.class));
      case UPDATE_AREA -> updateArea(before, require(payload, CommandPayloads.UpdateArea.class));
      case DELETE_AREA -> deleteArea(before, require(payload, CommandPayloads.DeleteArea.class));
      case CREATE_GRAVE ->
          createGrave(before, require(payload, CommandPayloads.CreateGrave.class), timestamp);
      case CREATE_GRAVE_GRID ->
          createGraveGrid(
              before, require(payload, CommandPayloads.CreateGraveGrid.class), timestamp);
      case FILL_GRAVE_RANGE ->
          fillGraveRange(before, require(payload, CommandPayloads.FillGraveRange.class), timestamp);
      case UPDATE_GRAVE_INFO ->
          updateGraveInfo(
              before, require(payload, CommandPayloads.UpdateGraveInfo.class), timestamp);
      case MOVE_GRAVES ->
          moveGraves(before, require(payload, CommandPayloads.MoveGraves.class), timestamp);
      case RESIZE_GRAVE ->
          resizeGrave(before, require(payload, CommandPayloads.ResizeGrave.class), timestamp);
      case COPY_GRAVES ->
          copyGraves(before, require(payload, CommandPayloads.CopyGraves.class), timestamp);
      case DELETE_GRAVES ->
          deleteGraves(before, require(payload, CommandPayloads.DeleteGraves.class));
      case NUMBER_GRAVES ->
          numberGraves(
              before,
              require(payload, CommandPayloads.NumberGraves.class),
              timestamp,
              httpSessionId,
              projectId);
      case CREATE_PERSON ->
          createPerson(before, require(payload, CommandPayloads.CreatePerson.class), timestamp);
      case UPDATE_PERSON ->
          updatePerson(before, require(payload, CommandPayloads.UpdatePerson.class), timestamp);
      case DELETE_PERSON ->
          deletePerson(before, require(payload, CommandPayloads.DeletePerson.class), timestamp);
      case ADD_ATTACHMENTS ->
          addAttachments(
              before,
              require(payload, CommandPayloads.AddAttachments.class),
              timestamp,
              httpSessionId,
              projectId);
      case UPDATE_ATTACHMENT ->
          updateAttachment(
              before, require(payload, CommandPayloads.UpdateAttachment.class), timestamp);
      case REORDER_ATTACHMENTS ->
          reorderAttachments(
              before, require(payload, CommandPayloads.ReorderAttachments.class), timestamp);
      case DELETE_ATTACHMENT ->
          deleteAttachment(
              before, require(payload, CommandPayloads.DeleteAttachment.class), timestamp);
    };
  }

  private Mutation setBackground(
      ProjectAggregate before,
      CommandPayloads.SetBackground payload,
      Instant timestamp,
      String sessionId,
      UUID projectId) {
    Path source =
        fileSelections.consume(
            payload.fileSelectionId(), sessionId, FileSelectionPurpose.BACKGROUND_IMPORT);
    Path root = openProjects.projectRoot(projectId);
    AssetIngestor ingestor = new AssetIngestor();
    AssetIngestor.PreparedAsset prepared =
        ingestor.prepare(source, true, root.resolve(".hakamap-staging"));
    AssetId id = new AssetId(uuids.next());
    String relative = "assets/backgrounds/" + id.value() + "." + prepared.storedExtension();
    Path target = root.resolve(relative);
    try {
      ingestor.place(prepared, target);
      AssetMetadata asset =
          new AssetMetadata(
              id,
              AssetType.BACKGROUND,
              Optional.empty(),
              prepared.originalFileName(),
              relative,
              prepared.sourceMediaType(),
              prepared.storedMediaType(),
              prepared.sizeBytes(),
              prepared.sha256(),
              timestamp,
              Optional.empty(),
              Optional.empty(),
              Optional.empty(),
              Optional.empty());
      Map<AssetId, AssetMetadata> assets = new LinkedHashMap<>(before.assets());
      before.background().ifPresent(background -> assets.remove(background.assetId()));
      assets.put(id, asset);
      BackgroundPlacement placement =
          new BackgroundPlacement(
              id,
              new MapPoint(payload.x(), payload.y()),
              new RotationDegrees(payload.rotation()),
              new BackgroundScale(payload.scaleX()),
              new BackgroundScale(payload.scaleY()));
      return mutation(
          aggregate(
              before,
              before.metadata(),
              Optional.of(placement),
              before.areas(),
              before.graves(),
              before.people(),
              assets));
    } catch (RuntimeException exception) {
      deleteQuietly(target);
      throw exception;
    } finally {
      if (prepared.temporary()) {
        deleteQuietly(prepared.preparedPath());
      }
    }
  }

  private Mutation rename(ProjectAggregate before, CommandPayloads.RenameProject payload) {
    ProjectMetadata old = before.metadata();
    ProjectAggregate candidate =
        aggregate(
            before,
            new ProjectMetadata(
                old.id(), new ProjectName(payload.name()), old.createdAt(), old.updatedAt()),
            before.background(),
            before.areas(),
            before.graves(),
            before.people(),
            before.assets());
    return mutation(candidate);
  }

  private Mutation transformBackground(
      ProjectAggregate before, CommandPayloads.TransformBackground payload) {
    BackgroundPlacement current =
        before.background().orElseThrow(() -> new EditingApiException("asset-not-found"));
    BackgroundPlacement transformed =
        new BackgroundPlacement(
            current.assetId(),
            new MapPoint(payload.x(), payload.y()),
            new RotationDegrees(payload.rotation()),
            new BackgroundScale(payload.scaleX()),
            new BackgroundScale(payload.scaleY()));
    return mutation(withBackground(before, Optional.of(transformed)));
  }

  private Mutation removeBackground(
      ProjectAggregate before, CommandPayloads.RemoveBackground ignored) {
    BackgroundPlacement current =
        before.background().orElseThrow(() -> new EditingApiException("asset-not-found"));
    Map<AssetId, AssetMetadata> assets = new LinkedHashMap<>(before.assets());
    assets.remove(current.assetId());
    return mutation(
        aggregate(
            before,
            before.metadata(),
            Optional.empty(),
            before.areas(),
            before.graves(),
            before.people(),
            assets));
  }

  private Mutation createArea(ProjectAggregate before, CommandPayloads.CreateArea payload) {
    requireClientRef(payload.clientRef());
    AreaColorPreset color =
        payload.colorPreset() == null
            ? before.nextAvailableAreaColor()
            : color(payload.colorPreset());
    Area area =
        new Area(
            new AreaId(uuids.next()),
            new AreaName(payload.name()),
            rectangle(payload.x(), payload.y(), payload.width(), payload.height()),
            color,
            payload.visible(),
            new DisplayOrder(before.areas().size()));
    Map<AreaId, Area> areas = new LinkedHashMap<>(before.areas());
    areas.put(area.id(), area);
    return new Mutation(
        withAreas(before, areas), createdResult(payload.clientRef(), area.id().value()));
  }

  private Mutation updateArea(ProjectAggregate before, CommandPayloads.UpdateArea payload) {
    AreaId id = new AreaId(payload.areaId());
    Area old = requireArea(before, id);
    Area updated =
        new Area(
            id,
            new AreaName(payload.name()),
            rectangle(payload.x(), payload.y(), payload.width(), payload.height()),
            color(payload.colorPreset()),
            payload.visible(),
            old.displayOrder());
    Map<AreaId, Area> areas = new LinkedHashMap<>(before.areas());
    areas.put(id, updated);
    return mutation(withAreas(before, areas));
  }

  private Mutation deleteArea(ProjectAggregate before, CommandPayloads.DeleteArea payload) {
    AreaId id = new AreaId(payload.areaId());
    requireArea(before, id);
    List<Area> ordered =
        before.areas().values().stream()
            .filter(area -> !area.id().equals(id))
            .sorted(Comparator.comparing(Area::displayOrder))
            .toList();
    Map<AreaId, Area> areas = new LinkedHashMap<>();
    for (int index = 0; index < ordered.size(); index++) {
      Area area = ordered.get(index).withDisplayOrder(new DisplayOrder(index));
      areas.put(area.id(), area);
    }
    return mutation(withAreas(before, areas));
  }

  private Mutation createGrave(
      ProjectAggregate before, CommandPayloads.CreateGrave payload, Instant timestamp) {
    requireClientRef(payload.clientRef());
    Grave grave =
        new Grave(
            new GraveId(uuids.next()),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            rectangle(payload.x(), payload.y(), payload.width(), payload.height()),
            RotationDegrees.ZERO,
            timestamp);
    Map<GraveId, Grave> graves = new LinkedHashMap<>(before.graves());
    graves.put(grave.id(), grave);
    return new Mutation(
        withGraves(before, graves), createdResult(payload.clientRef(), grave.id().value()));
  }

  private Mutation createGraveGrid(
      ProjectAggregate before, CommandPayloads.CreateGraveGrid payload, Instant timestamp) {
    requireClientRef(payload.clientRefPrefix());
    List<Grave> created =
        graveGeneration.matrix(
            payload.rows(),
            payload.columns(),
            new MapPoint(payload.x(), payload.y()),
            new MapSize(payload.graveWidth(), payload.graveHeight()),
            payload.horizontalGap(),
            payload.verticalGap(),
            timestamp,
            uuids);
    return addGenerated(before, created, payload.clientRefPrefix());
  }

  private Mutation fillGraveRange(
      ProjectAggregate before, CommandPayloads.FillGraveRange payload, Instant timestamp) {
    requireClientRef(payload.clientRefPrefix());
    List<Grave> created =
        graveGeneration.fill(
            rectangle(
                payload.rangeX(), payload.rangeY(), payload.rangeWidth(), payload.rangeHeight()),
            new MapSize(payload.graveWidth(), payload.graveHeight()),
            payload.horizontalGap(),
            payload.verticalGap(),
            timestamp,
            uuids);
    return addGenerated(before, created, payload.clientRefPrefix());
  }

  private Mutation addGenerated(
      ProjectAggregate before, List<Grave> created, String clientRefPrefix) {
    Map<GraveId, Grave> graves = new LinkedHashMap<>(before.graves());
    created.forEach(grave -> graves.put(grave.id(), grave));
    List<ClientReferenceResponse> refs = new ArrayList<>();
    for (int index = 0; index < created.size(); index++) {
      refs.add(
          new ClientReferenceResponse(
              clientRefPrefix + "-" + index, created.get(index).id().value()));
    }
    return new Mutation(withGraves(before, graves), new CommandResultResponse(refs, List.of()));
  }

  private Mutation updateGraveInfo(
      ProjectAggregate before, CommandPayloads.UpdateGraveInfo payload, Instant timestamp) {
    GraveId id = new GraveId(payload.graveId());
    Grave old = requireGrave(before, id);
    Grave updated =
        new Grave(
            id,
            optional(payload.managementNumber(), ManagementNumber::new),
            optional(payload.name(), GraveName::new),
            optional(payload.notes(), GraveNotes::new),
            old.rectangle(),
            old.rotation(),
            timestamp);
    return mutation(replaceGrave(before, updated));
  }

  private Mutation moveGraves(
      ProjectAggregate before, CommandPayloads.MoveGraves payload, Instant timestamp) {
    requireUniqueNonEmpty(payload.graveIds());
    Map<GraveId, Grave> graves = new LinkedHashMap<>(before.graves());
    payload.graveIds().stream()
        .map(GraveId::new)
        .forEach(
            id -> {
              Grave old = requireGrave(before, id);
              graves.put(
                  id,
                  old.move(
                      old.rectangle().translate(payload.deltaX(), payload.deltaY()), timestamp));
            });
    return mutation(withGraves(before, graves));
  }

  private Mutation resizeGrave(
      ProjectAggregate before, CommandPayloads.ResizeGrave payload, Instant timestamp) {
    Grave old = requireGrave(before, new GraveId(payload.graveId()));
    return mutation(
        replaceGrave(
            before,
            old.move(
                rectangle(payload.x(), payload.y(), payload.width(), payload.height()),
                timestamp)));
  }

  private Mutation copyGraves(
      ProjectAggregate before, CommandPayloads.CopyGraves payload, Instant timestamp) {
    requireUniqueNonEmpty(payload.graveIds());
    List<Grave> source =
        payload.graveIds().stream().map(GraveId::new).map(id -> requireGrave(before, id)).toList();
    List<Grave> copies =
        graveGeneration.copies(source, payload.deltaX(), payload.deltaY(), timestamp, uuids);
    Map<GraveId, Grave> graves = new LinkedHashMap<>(before.graves());
    copies.forEach(grave -> graves.put(grave.id(), grave));
    return mutation(withGraves(before, graves));
  }

  private Mutation deleteGraves(ProjectAggregate before, CommandPayloads.DeleteGraves payload) {
    requireUniqueNonEmpty(payload.graveIds());
    Set<GraveId> ids = payload.graveIds().stream().map(GraveId::new).collect(Collectors.toSet());
    ids.forEach(id -> requireGrave(before, id));
    Map<GraveId, Grave> graves = new LinkedHashMap<>(before.graves());
    ids.forEach(graves::remove);
    Map<PersonId, Person> people =
        before.people().values().stream()
            .filter(person -> !ids.contains(person.graveId()))
            .collect(toLinkedMap(Person::id));
    Map<AssetId, AssetMetadata> assets =
        before.assets().values().stream()
            .filter(
                asset -> asset.graveId().isEmpty() || !ids.contains(asset.graveId().orElseThrow()))
            .collect(toLinkedMap(AssetMetadata::id));
    return mutation(
        aggregate(
            before,
            before.metadata(),
            before.background(),
            before.areas(),
            graves,
            people,
            assets));
  }

  private Mutation numberGraves(
      ProjectAggregate before,
      CommandPayloads.NumberGraves payload,
      Instant timestamp,
      String sessionId,
      UUID projectId) {
    List<NumberingAssignment> assignments =
        tokens.consumeNumbering(
            payload.numberingPreviewToken(), sessionId, projectId, session(projectId).revision());
    Map<GraveId, Grave> graves = new LinkedHashMap<>(before.graves());
    for (NumberingAssignment assignment : assignments) {
      Grave old = requireGrave(before, assignment.graveId());
      graves.put(old.id(), old.number(assignment.managementNumber(), timestamp));
    }
    return new Mutation(
        withGraves(before, graves),
        new CommandResultResponse(
            List.of(),
            assignments.stream()
                .map(
                    assignment ->
                        new NumberingResultResponse(
                            assignment.graveId().value(), assignment.managementNumber().value()))
                .toList()));
  }

  private Mutation createPerson(
      ProjectAggregate before, CommandPayloads.CreatePerson payload, Instant timestamp) {
    requireClientRef(payload.clientRef());
    GraveId graveId = new GraveId(payload.graveId());
    Grave grave = requireGrave(before, graveId);
    int order =
        (int)
            before.people().values().stream()
                .filter(person -> person.graveId().equals(graveId))
                .count();
    Person person =
        new Person(
            new PersonId(uuids.next()),
            graveId,
            optional(payload.name(), PersonName::new),
            optional(payload.posthumousName(), PosthumousName::new),
            timestamp,
            timestamp,
            new DisplayOrder(order));
    Map<PersonId, Person> people = new LinkedHashMap<>(before.people());
    people.put(person.id(), person);
    Map<GraveId, Grave> graves = new LinkedHashMap<>(before.graves());
    graves.put(graveId, grave.move(grave.rectangle(), timestamp));
    ProjectAggregate candidate =
        aggregate(
            before,
            before.metadata(),
            before.background(),
            before.areas(),
            graves,
            people,
            before.assets());
    return new Mutation(candidate, createdResult(payload.clientRef(), person.id().value()));
  }

  private Mutation updatePerson(
      ProjectAggregate before, CommandPayloads.UpdatePerson payload, Instant timestamp) {
    PersonId id = new PersonId(payload.personId());
    Person old = requirePerson(before, id);
    Person updated =
        new Person(
            id,
            old.graveId(),
            optional(payload.name(), PersonName::new),
            optional(payload.posthumousName(), PosthumousName::new),
            old.createdAt(),
            timestamp,
            old.displayOrder());
    Map<PersonId, Person> people = new LinkedHashMap<>(before.people());
    people.put(id, updated);
    return mutation(withPersonAndGraveTimestamp(before, people, old.graveId(), timestamp));
  }

  private Mutation deletePerson(
      ProjectAggregate before, CommandPayloads.DeletePerson payload, Instant timestamp) {
    Person old = requirePerson(before, new PersonId(payload.personId()));
    List<Person> owned =
        before.people().values().stream()
            .filter(person -> person.graveId().equals(old.graveId()))
            .filter(person -> !person.id().equals(old.id()))
            .sorted(Comparator.comparing(Person::displayOrder))
            .toList();
    Map<PersonId, Person> people = new LinkedHashMap<>(before.people());
    people.remove(old.id());
    for (int index = 0; index < owned.size(); index++) {
      Person reordered = owned.get(index).withDisplayOrder(new DisplayOrder(index));
      people.put(reordered.id(), reordered);
    }
    return mutation(withPersonAndGraveTimestamp(before, people, old.graveId(), timestamp));
  }

  private Mutation updateAttachment(
      ProjectAggregate before, CommandPayloads.UpdateAttachment payload, Instant timestamp) {
    AssetId id = new AssetId(payload.assetId());
    AssetMetadata old = requireAttachment(before, id);
    AssetMetadata updated =
        new AssetMetadata(
            old.id(),
            old.type(),
            old.graveId(),
            old.originalFileName(),
            old.relativePath(),
            old.sourceMediaType(),
            old.storedMediaType(),
            old.sizeBytes(),
            old.sha256(),
            old.createdAt(),
            optional(payload.displayName(), AssetDisplayName::new),
            optional(payload.description(), AssetDescription::new),
            Optional.of(timestamp),
            old.displayOrder());
    return mutation(replaceAssetAndTouchGrave(before, updated, timestamp));
  }

  private Mutation addAttachments(
      ProjectAggregate before,
      CommandPayloads.AddAttachments payload,
      Instant timestamp,
      String sessionId,
      UUID projectId) {
    requireUniqueNonEmpty(payload.fileSelectionIds());
    GraveId graveId = new GraveId(payload.graveId());
    Grave grave = requireGrave(before, graveId);
    long existing =
        before.assets().values().stream()
            .filter(asset -> asset.type() == AssetType.ATTACHMENT)
            .filter(asset -> asset.graveId().orElseThrow().equals(graveId))
            .count();
    if (existing + payload.fileSelectionIds().size() > 20) {
      throw new EditingApiException("asset-count-exceeded");
    }
    Path root = openProjects.projectRoot(projectId);
    AssetIngestor ingestor = new AssetIngestor();
    List<AssetIngestor.PreparedAsset> prepared = new ArrayList<>();
    for (UUID selectionId : payload.fileSelectionIds()) {
      Path source =
          fileSelections.consume(selectionId, sessionId, FileSelectionPurpose.ATTACHMENT_IMPORT);
      prepared.add(ingestor.prepare(source, false, root.resolve(".hakamap-staging")));
    }
    Map<AssetId, AssetMetadata> assets = new LinkedHashMap<>(before.assets());
    List<Path> placed = new ArrayList<>();
    try {
      for (int index = 0; index < prepared.size(); index++) {
        AssetIngestor.PreparedAsset item = prepared.get(index);
        AssetId id = new AssetId(uuids.next());
        String relative = "assets/attachments/" + id.value() + "." + item.storedExtension();
        Path target = root.resolve(relative);
        ingestor.place(item, target);
        placed.add(target);
        assets.put(
            id,
            new AssetMetadata(
                id,
                AssetType.ATTACHMENT,
                Optional.of(graveId),
                item.originalFileName(),
                relative,
                item.sourceMediaType(),
                item.storedMediaType(),
                item.sizeBytes(),
                item.sha256(),
                timestamp,
                Optional.of(new AssetDisplayName(item.originalFileName())),
                Optional.empty(),
                Optional.of(timestamp),
                Optional.of(new DisplayOrder(Math.toIntExact(existing) + index))));
      }
      Map<GraveId, Grave> graves = new LinkedHashMap<>(before.graves());
      graves.put(graveId, grave.move(grave.rectangle(), timestamp));
      return mutation(
          aggregate(
              before,
              before.metadata(),
              before.background(),
              before.areas(),
              graves,
              before.people(),
              assets));
    } catch (RuntimeException exception) {
      placed.forEach(this::deleteQuietly);
      throw exception;
    } finally {
      prepared.stream()
          .filter(AssetIngestor.PreparedAsset::temporary)
          .map(AssetIngestor.PreparedAsset::preparedPath)
          .forEach(this::deleteQuietly);
    }
  }

  private Mutation reorderAttachments(
      ProjectAggregate before, CommandPayloads.ReorderAttachments payload, Instant timestamp) {
    requireUniqueNonEmpty(payload.orderedAssetIds());
    GraveId graveId = new GraveId(payload.graveId());
    requireGrave(before, graveId);
    List<AssetMetadata> owned =
        before.assets().values().stream()
            .filter(asset -> asset.type() == AssetType.ATTACHMENT)
            .filter(asset -> asset.graveId().orElseThrow().equals(graveId))
            .toList();
    Set<UUID> expected =
        owned.stream().map(asset -> asset.id().value()).collect(Collectors.toSet());
    if (!expected.equals(Set.copyOf(payload.orderedAssetIds()))) {
      throw new EditingApiException("asset-selection-invalid");
    }
    Map<AssetId, AssetMetadata> assets = new LinkedHashMap<>(before.assets());
    for (int index = 0; index < payload.orderedAssetIds().size(); index++) {
      AssetMetadata old =
          requireAttachment(before, new AssetId(payload.orderedAssetIds().get(index)));
      assets.put(
          old.id(),
          new AssetMetadata(
              old.id(),
              old.type(),
              old.graveId(),
              old.originalFileName(),
              old.relativePath(),
              old.sourceMediaType(),
              old.storedMediaType(),
              old.sizeBytes(),
              old.sha256(),
              old.createdAt(),
              old.displayName(),
              old.description(),
              Optional.of(timestamp),
              Optional.of(new DisplayOrder(index))));
    }
    return mutation(withAssetsAndTouchGrave(before, assets, graveId, timestamp));
  }

  private Mutation deleteAttachment(
      ProjectAggregate before, CommandPayloads.DeleteAttachment payload, Instant timestamp) {
    AssetMetadata old = requireAttachment(before, new AssetId(payload.assetId()));
    GraveId graveId = old.graveId().orElseThrow();
    Map<AssetId, AssetMetadata> assets = new LinkedHashMap<>(before.assets());
    assets.remove(old.id());
    List<AssetMetadata> owned =
        assets.values().stream()
            .filter(asset -> asset.type() == AssetType.ATTACHMENT)
            .filter(asset -> asset.graveId().orElseThrow().equals(graveId))
            .sorted(Comparator.comparing(asset -> asset.displayOrder().orElseThrow()))
            .toList();
    for (int index = 0; index < owned.size(); index++) {
      AssetMetadata item = owned.get(index);
      assets.put(
          item.id(),
          new AssetMetadata(
              item.id(),
              item.type(),
              item.graveId(),
              item.originalFileName(),
              item.relativePath(),
              item.sourceMediaType(),
              item.storedMediaType(),
              item.sizeBytes(),
              item.sha256(),
              item.createdAt(),
              item.displayName(),
              item.description(),
              item.updatedAt(),
              Optional.of(new DisplayOrder(index))));
    }
    return mutation(withAssetsAndTouchGrave(before, assets, graveId, timestamp));
  }

  private CommandResponse response(
      ProjectEditingSession session, ProjectChangeSet changeSet, CommandResultResponse result) {
    ProjectAggregate current = session.current();
    List<PersonChangeResponse> personChanges = personChanges(current, changeSet);
    return new CommandResponse(
        "applied",
        session.revision(),
        session.dirty(),
        changeSet.areaDeltas().stream()
            .flatMap(delta -> delta.after().stream())
            .map(this::toArea)
            .toList(),
        deletedIds(changeSet.areaDeltas(), AreaId::value),
        changeSet.graveDeltas().stream()
            .flatMap(delta -> delta.after().stream())
            .map(this::toGrave)
            .toList(),
        deletedIds(changeSet.graveDeltas(), GraveId::value),
        personChanges,
        changeSet.assetDeltas().stream()
            .flatMap(delta -> delta.after().stream())
            .map(this::toAsset)
            .toList(),
        deletedIds(changeSet.assetDeltas(), AssetId::value),
        allGraveStates(current),
        List.of(),
        historySummary(session),
        result);
  }

  private CommandResponse emptyResponse(ProjectEditingSession session, String status) {
    return new CommandResponse(
        status,
        session.revision(),
        session.dirty(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        historySummary(session),
        emptyResult());
  }

  private CommandResponse stateTransitionResponse(
      ProjectEditingSession session, ProjectAggregate before, CommandType type) {
    ProjectChangeSet synthetic =
        deltas.between(before, session.current(), type, clock.instant(), uuids.next());
    return response(session, synthetic, emptyResult());
  }

  private List<PersonChangeResponse> personChanges(
      ProjectAggregate current, ProjectChangeSet changeSet) {
    Map<GraveId, List<Person>> upserts = new LinkedHashMap<>();
    Map<GraveId, List<UUID>> deletes = new LinkedHashMap<>();
    for (EntityDelta<PersonId, Person> delta : changeSet.personDeltas()) {
      delta
          .after()
          .ifPresent(
              person ->
                  upserts
                      .computeIfAbsent(person.graveId(), ignored -> new ArrayList<>())
                      .add(person));
      delta
          .before()
          .filter(ignored -> delta.after().isEmpty())
          .ifPresent(
              person ->
                  deletes
                      .computeIfAbsent(person.graveId(), ignored -> new ArrayList<>())
                      .add(person.id().value()));
    }
    Set<GraveId> owners = new java.util.LinkedHashSet<>(upserts.keySet());
    owners.addAll(deletes.keySet());
    return owners.stream()
        .map(
            graveId ->
                new PersonChangeResponse(
                    graveId.value(),
                    upserts.getOrDefault(graveId, List.of()).stream()
                        .sorted(Comparator.comparing(Person::displayOrder))
                        .map(this::toPerson)
                        .toList(),
                    List.copyOf(deletes.getOrDefault(graveId, List.of())),
                    (int)
                        current.people().values().stream()
                            .filter(person -> person.graveId().equals(graveId))
                            .count()))
        .toList();
  }

  private List<WarningResponse> newPlacementWarnings(
      ProjectAggregate before, ProjectAggregate after) {
    Map<DomainWarningCode, Integer> counts = new LinkedHashMap<>();

    List<DomainWarningCode> placementWarningCodes =
        List.of(DomainWarningCode.UNASSIGNED, DomainWarningCode.OUTSIDE_AREA_BOUNDS);
    for (Grave grave : after.graves().values()) {
      GraveStatus next = after.graveStatus(grave.id());
      Set<DomainWarningCode> prior =
          before.graves().containsKey(grave.id())
              ? before.graveStatus(grave.id()).warnings()
              : Set.of();
      for (DomainWarningCode code : placementWarningCodes) {
        if (next.warnings().contains(code) && !prior.contains(code)) {
          counts.merge(code, 1, Integer::sum);
        }
      }
    }
    return counts.entrySet().stream()
        .map(entry -> new WarningResponse(apiEnum(entry.getKey()), entry.getValue()))
        .toList();
  }

  private List<GraveStateResponse> allGraveStates(ProjectAggregate project) {
    return project.graves().values().stream()
        .sorted(Comparator.comparing(grave -> grave.id().value()))
        .map(
            grave -> {
              GraveStatus status = project.graveStatus(grave.id());
              return new GraveStateResponse(
                  grave.id().value(),
                  status.areaId().map(AreaId::value).orElse(null),
                  apiEnum(status.completionStatus()),
                  status.incompleteReasons().stream().map(this::apiEnum).sorted().toList(),
                  status.warnings().stream().map(this::apiEnum).sorted().toList());
            })
        .toList();
  }

  private ProjectResponse toProject(ProjectAggregate project) {
    ProjectMetadata metadata = project.metadata();
    return new ProjectResponse(
        metadata.id().value(), metadata.name().value(), metadata.createdAt(), metadata.updatedAt());
  }

  private BackgroundResponse toBackground(BackgroundPlacement value) {
    return new BackgroundResponse(
        value.assetId().value(),
        value.position().x(),
        value.position().y(),
        value.rotation().value(),
        value.scaleX().value(),
        value.scaleY().value());
  }

  private AreaResponse toArea(Area value) {
    return new AreaResponse(
        value.id().value(),
        value.name().value(),
        value.rectangle().left(),
        value.rectangle().top(),
        value.rectangle().size().width(),
        value.rectangle().size().height(),
        apiEnum(value.color()),
        value.visible(),
        value.displayOrder().value());
  }

  private GraveResponse toGrave(Grave value) {
    return new GraveResponse(
        value.id().value(),
        value.managementNumber().map(ManagementNumber::value).orElse(null),
        value.name().map(GraveName::value).orElse(null),
        value.notes().map(GraveNotes::value).orElse(null),
        value.rectangle().left(),
        value.rectangle().top(),
        value.rectangle().size().width(),
        value.rectangle().size().height(),
        value.rotation().value(),
        value.updatedAt());
  }

  private PersonResponse toPerson(Person value) {
    return new PersonResponse(
        value.id().value(),
        value.graveId().value(),
        value.name().map(PersonName::value).orElse(null),
        value.posthumousName().map(PosthumousName::value).orElse(null),
        value.createdAt(),
        value.updatedAt(),
        value.displayOrder().value());
  }

  private AssetResponse toAsset(AssetMetadata value) {
    return new AssetResponse(
        value.id().value(),
        apiEnum(value.type()),
        value.graveId().map(GraveId::value).orElse(null),
        value.displayName().map(AssetDisplayName::value).orElse(null),
        value.description().map(AssetDescription::value).orElse(null),
        value.storedMediaType(),
        value.sizeBytes(),
        value.createdAt(),
        value.updatedAt().orElse(null),
        value.displayOrder().map(DisplayOrder::value).orElse(null));
  }

  private HistorySummaryResponse historySummary(ProjectEditingSession session) {
    return new HistorySummaryResponse(
        session.undoSize() > 0, session.redoSize() > 0, session.undoSize(), session.redoSize());
  }

  private CapabilitiesResponse capabilities(ProjectEditingSession session) {
    return new CapabilitiesResponse(
        session.dirty() && !session.editingStopped(),
        session.undoSize() > 0 && !session.editingStopped(),
        session.redoSize() > 0 && !session.editingStopped(),
        !session.editingStopped());
  }

  private ProjectEditingSession session(UUID projectId) {
    return openProjects.editingSession(projectId, fingerprints);
  }

  private Mutation mutation(ProjectAggregate candidate) {
    return new Mutation(candidate, emptyResult());
  }

  private CommandResultResponse emptyResult() {
    return new CommandResultResponse(List.of(), List.of());
  }

  private CommandResultResponse createdResult(String clientRef, UUID id) {
    return new CommandResultResponse(
        List.of(new ClientReferenceResponse(clientRef, id)), List.of());
  }

  private ProjectAggregate withAreas(ProjectAggregate current, Map<AreaId, Area> areas) {
    return aggregate(
        current,
        current.metadata(),
        current.background(),
        areas,
        current.graves(),
        current.people(),
        current.assets());
  }

  private ProjectAggregate withGraves(ProjectAggregate current, Map<GraveId, Grave> graves) {
    return aggregate(
        current,
        current.metadata(),
        current.background(),
        current.areas(),
        graves,
        current.people(),
        current.assets());
  }

  private ProjectAggregate replaceGrave(ProjectAggregate current, Grave grave) {
    Map<GraveId, Grave> graves = new LinkedHashMap<>(current.graves());
    graves.put(grave.id(), grave);
    return withGraves(current, graves);
  }

  private ProjectAggregate withBackground(
      ProjectAggregate current, Optional<BackgroundPlacement> background) {
    return aggregate(
        current,
        current.metadata(),
        background,
        current.areas(),
        current.graves(),
        current.people(),
        current.assets());
  }

  private ProjectAggregate withPersonAndGraveTimestamp(
      ProjectAggregate current, Map<PersonId, Person> people, GraveId graveId, Instant timestamp) {
    Grave grave = requireGrave(current, graveId);
    Map<GraveId, Grave> graves = new LinkedHashMap<>(current.graves());
    graves.put(graveId, grave.move(grave.rectangle(), timestamp));
    return aggregate(
        current,
        current.metadata(),
        current.background(),
        current.areas(),
        graves,
        people,
        current.assets());
  }

  private ProjectAggregate replaceAssetAndTouchGrave(
      ProjectAggregate current, AssetMetadata asset, Instant timestamp) {
    Map<AssetId, AssetMetadata> assets = new LinkedHashMap<>(current.assets());
    assets.put(asset.id(), asset);
    return withAssetsAndTouchGrave(current, assets, asset.graveId().orElseThrow(), timestamp);
  }

  private ProjectAggregate withAssetsAndTouchGrave(
      ProjectAggregate current,
      Map<AssetId, AssetMetadata> assets,
      GraveId graveId,
      Instant timestamp) {
    Grave grave = requireGrave(current, graveId);
    Map<GraveId, Grave> graves = new LinkedHashMap<>(current.graves());
    graves.put(graveId, grave.move(grave.rectangle(), timestamp));
    return aggregate(
        current,
        current.metadata(),
        current.background(),
        current.areas(),
        graves,
        current.people(),
        assets);
  }

  private ProjectAggregate aggregate(
      ProjectAggregate ignored,
      ProjectMetadata metadata,
      Optional<BackgroundPlacement> background,
      Map<AreaId, Area> areas,
      Map<GraveId, Grave> graves,
      Map<PersonId, Person> people,
      Map<AssetId, AssetMetadata> assets) {
    return new ProjectAggregate(
        metadata, background, areas.values(), graves.values(), people.values(), assets.values());
  }

  private Area requireArea(ProjectAggregate project, AreaId id) {
    Area value = project.areas().get(id);
    if (value == null) {
      throw new ProjectInvariantException("area-not-found");
    }
    return value;
  }

  private Grave requireGrave(ProjectAggregate project, GraveId id) {
    Grave value = project.graves().get(id);
    if (value == null) {
      throw new ProjectInvariantException("grave-not-found");
    }
    return value;
  }

  private Person requirePerson(ProjectAggregate project, PersonId id) {
    Person value = project.people().get(id);
    if (value == null) {
      throw new ProjectInvariantException("person-not-found");
    }
    return value;
  }

  private AssetMetadata requireAttachment(ProjectAggregate project, AssetId id) {
    AssetMetadata value = project.assets().get(id);
    if (value == null || value.type() != AssetType.ATTACHMENT) {
      throw new EditingApiException("asset-not-found");
    }
    return value;
  }

  private MapRectangle rectangle(BigDecimal x, BigDecimal y, BigDecimal width, BigDecimal height) {
    return new MapRectangle(x, y, width, height);
  }

  private AreaColorPreset color(String value) {
    try {
      return AreaColorPreset.valueOf(value.replace('-', '_').toUpperCase(java.util.Locale.ROOT));
    } catch (RuntimeException exception) {
      throw new EditingApiException("request-field-invalid");
    }
  }

  private <T> T require(CommandPayloads.CommandPayload payload, Class<T> requiredType) {
    if (!requiredType.isInstance(payload)) {
      throw new EditingApiException("request-unknown-command");
    }
    return requiredType.cast(payload);
  }

  private void requireClientRef(String value) {
    if (value == null || !value.matches("^[A-Za-z0-9_-]{1,64}$")) {
      throw new EditingApiException("request-field-invalid");
    }
  }

  private void requireUniqueNonEmpty(List<UUID> values) {
    if (values == null || values.isEmpty() || Set.copyOf(values).size() != values.size()) {
      throw new EditingApiException("request-field-invalid");
    }
  }

  private <T> Optional<T> optional(String value, Function<String, T> constructor) {
    return value == null ? Optional.empty() : Optional.of(constructor.apply(value));
  }

  private <T, I> java.util.stream.Collector<T, ?, Map<I, T>> toLinkedMap(Function<T, I> id) {
    return Collectors.toMap(id, Function.identity(), (first, second) -> first, LinkedHashMap::new);
  }

  private <I, T> List<UUID> deletedIds(List<EntityDelta<I, T>> deltas, Function<I, UUID> uuid) {
    return deltas.stream()
        .filter(delta -> delta.after().isEmpty())
        .map(delta -> uuid.apply(delta.id()))
        .toList();
  }

  private String apiCommandType(CommandType type) {
    String[] parts = type.name().toLowerCase(java.util.Locale.ROOT).split("_");
    StringBuilder result = new StringBuilder(parts[0]);
    for (int index = 1; index < parts.length; index++) {
      result
          .append(Character.toUpperCase(parts[index].charAt(0)))
          .append(parts[index].substring(1));
    }
    return result.toString();
  }

  private void deleteQuietly(Path path) {
    try {
      Files.deleteIfExists(path);
    } catch (java.io.IOException ignored) {
      // 後続の既知一時ファイル清掃へ回す。
    }
  }

  private String apiEnum(Enum<?> value) {
    return value.name().toLowerCase(java.util.Locale.ROOT);
  }

  private record Mutation(ProjectAggregate candidate, CommandResultResponse result) {}

  public record AssetContent(Path path, String mediaType, long sizeBytes) {}
}
