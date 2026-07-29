package jp.hakamap.project.application.editing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.imageio.ImageIO;
import jp.hakamap.infrastructure.fileselection.FileSelectionMode;
import jp.hakamap.infrastructure.fileselection.FileSelectionPurpose;
import jp.hakamap.infrastructure.fileselection.FileSelectionService;
import jp.hakamap.infrastructure.persistence.schema.ClasspathJsonSchemaValidator;
import jp.hakamap.persistence.json.DefensiveJsonCodec;
import jp.hakamap.persistence.json.mapper.ProjectFileV1Mapper;
import jp.hakamap.persistence.json.repository.FileProjectRepository;
import jp.hakamap.persistence.json.repository.ProjectRepository;
import jp.hakamap.persistence.json.validation.ProjectAssetFileValidator;
import jp.hakamap.project.application.catalog.OpenProjectManager;
import jp.hakamap.project.application.history.CommandType;
import jp.hakamap.project.application.history.EditingSessionException;
import jp.hakamap.project.application.history.ProjectFingerprintCalculator;
import jp.hakamap.project.domain.model.Grave;
import jp.hakamap.project.domain.model.Person;
import jp.hakamap.project.domain.model.ProjectAggregate;
import jp.hakamap.project.domain.model.ProjectMetadata;
import jp.hakamap.project.domain.value.DisplayOrder;
import jp.hakamap.project.domain.value.GraveId;
import jp.hakamap.project.domain.value.MapRectangle;
import jp.hakamap.project.domain.value.PersonId;
import jp.hakamap.project.domain.value.PersonName;
import jp.hakamap.project.domain.value.ProjectId;
import jp.hakamap.project.domain.value.ProjectName;
import jp.hakamap.project.domain.value.RotationDegrees;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectEditingApiServiceTest {
  static {
    System.setProperty("java.awt.headless", "true");
  }

  private static final Instant NOW = Instant.parse("2026-07-29T00:00:00Z");

  private static final UUID PROJECT_ID = UUID.fromString("97982d08-2112-47f3-bf17-5806c6d40fdb");

  @TempDir Path temporaryDirectory;

  @Test
  void appliesTypedCommandsReturnsConfirmedDiffAndSupportsUndoRedo() throws Exception {
    TestContext context = context(emptyProject());
    ProjectEditingApiService service = context.service();

    Object areaResult =
        service.execute(
            PROJECT_ID,
            "session-a",
            0,
            CommandType.CREATE_AREA,
            new CommandPayloads.CreateArea(
                "area-1",
                "第一",
                java.math.BigDecimal.ZERO,
                java.math.BigDecimal.ZERO,
                java.math.BigDecimal.valueOf(100),
                java.math.BigDecimal.valueOf(100),
                "blue",
                true));
    assertThat(areaResult).isInstanceOf(EditingApiModels.CommandResponse.class);
    assertThat(((EditingApiModels.CommandResponse) areaResult).revision()).isEqualTo(1);

    Object graveResult =
        service.execute(
            PROJECT_ID,
            "session-a",
            1,
            CommandType.CREATE_GRAVE,
            new CommandPayloads.CreateGrave(
                "grave-1",
                java.math.BigDecimal.TEN,
                java.math.BigDecimal.TEN,
                java.math.BigDecimal.TEN,
                java.math.BigDecimal.TEN));
    assertThat(graveResult).isInstanceOf(EditingApiModels.CommandResponse.class);
    EditingApiModels.CommandResponse applied = (EditingApiModels.CommandResponse) graveResult;
    assertThat(applied.revision()).isEqualTo(2);
    assertThat(applied.upsertedGraves()).hasSize(1);
    assertThat(service.snapshot(PROJECT_ID).getClass().getRecordComponents())
        .extracting(java.lang.reflect.RecordComponent::getName)
        .doesNotContain("people");

    assertThat(service.undo(PROJECT_ID, 2).revision()).isEqualTo(3);
    assertThat(service.snapshot(PROJECT_ID).graves()).isEmpty();
    assertThat(service.redo(PROJECT_ID, 3).revision()).isEqualTo(4);
    assertThat(service.snapshot(PROJECT_ID).graves()).hasSize(1);
    assertThat(service.history(PROJECT_ID).items()).hasSize(2);
  }

  @Test
  void requiresConfirmationForNewUnassignedWarningAndRejectsOldRevision() throws Exception {
    TestContext context = context(emptyProject());
    ProjectEditingApiService service = context.service();

    Object result =
        service.execute(
            PROJECT_ID,
            "session-a",
            0,
            CommandType.CREATE_GRAVE,
            new CommandPayloads.CreateGrave(
                "grave-1",
                java.math.BigDecimal.ZERO,
                java.math.BigDecimal.ZERO,
                java.math.BigDecimal.TEN,
                java.math.BigDecimal.TEN));

    assertThat(result).isInstanceOf(EditingApiModels.ConfirmationRequiredResponse.class);
    var confirmation = (EditingApiModels.ConfirmationRequiredResponse) result;
    assertThat(service.snapshot(PROJECT_ID).revision()).isZero();
    assertThat(confirmation.warnings())
        .extracting(EditingApiModels.WarningResponse::code)
        .contains("unassigned");
    assertThat(
            service.confirm(
                PROJECT_ID, "session-a", confirmation.confirmationToken(), confirmation.revision()))
        .extracting(EditingApiModels.CommandResponse::revision)
        .isEqualTo(1L);
    assertThatThrownBy(
            () ->
                service.execute(
                    PROJECT_ID,
                    "session-a",
                    0,
                    CommandType.RENAME_PROJECT,
                    new CommandPayloads.RenameProject("変更後")))
        .isInstanceOf(EditingSessionException.class)
        .hasMessage("project-revision-conflict");
  }

  @Test
  void returnsNoChangeWithoutIncrementingRevisionOrHistory() throws Exception {
    TestContext context = context(emptyProject());

    Object result =
        context
            .service()
            .execute(
                PROJECT_ID,
                "session-a",
                0,
                CommandType.RENAME_PROJECT,
                new CommandPayloads.RenameProject("テスト"));

    assertThat(result).isInstanceOf(EditingApiModels.CommandResponse.class);
    assertThat((EditingApiModels.CommandResponse) result)
        .extracting(
            EditingApiModels.CommandResponse::status, EditingApiModels.CommandResponse::revision)
        .containsExactly("noChange", 0L);
    assertThat(context.service().history(PROJECT_ID).items()).isEmpty();
  }

  @Test
  void pagesOnlyPeopleOwnedBySelectedGraveAndRejectsStaleCursor() throws Exception {
    GraveId graveId = new GraveId(UUID.fromString("8644022a-bca2-4c3e-b811-19c28b7a2d58"));
    Grave grave =
        new Grave(
            graveId,
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            new MapRectangle(
                java.math.BigDecimal.ZERO,
                java.math.BigDecimal.ZERO,
                java.math.BigDecimal.TEN,
                java.math.BigDecimal.TEN),
            RotationDegrees.ZERO,
            NOW);
    List<Person> people = new ArrayList<>();
    for (int index = 0; index < 101; index++) {
      people.add(
          new Person(
              new PersonId(UUID.randomUUID()),
              graveId,
              Optional.of(new PersonName("人物" + index)),
              Optional.empty(),
              NOW,
              NOW,
              new DisplayOrder(index)));
    }
    ProjectAggregate project =
        new ProjectAggregate(
            metadata(), Optional.empty(), List.of(), List.of(grave), people, List.of());
    TestContext context = context(project);
    ProjectEditingApiService service = context.service();

    EditingApiModels.GravePeoplePageResponse first =
        service.people(PROJECT_ID, graveId.value(), null, "session-a");
    assertThat(first.items()).hasSize(100);
    assertThat(first.totalCount()).isEqualTo(101);
    assertThat(first.nextCursor()).isNotBlank();
    assertThat(service.people(PROJECT_ID, graveId.value(), first.nextCursor(), "session-a").items())
        .hasSize(1);

    Object rename =
        service.execute(
            PROJECT_ID,
            "session-a",
            0,
            CommandType.RENAME_PROJECT,
            new CommandPayloads.RenameProject("変更後"));
    assertThat(rename).isInstanceOf(EditingApiModels.CommandResponse.class);
    assertThatThrownBy(
            () -> service.people(PROJECT_ID, graveId.value(), first.nextCursor(), "session-a"))
        .isInstanceOf(EditingApiException.class)
        .hasMessage("request-field-invalid");
  }

  @Test
  void addsValidatedAttachmentAsOneCommandAndServesOnlyManagedAsset() throws Exception {
    GraveId graveId = new GraveId(UUID.fromString("8644022a-bca2-4c3e-b811-19c28b7a2d58"));
    Grave grave =
        new Grave(
            graveId,
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            new MapRectangle(
                java.math.BigDecimal.ZERO,
                java.math.BigDecimal.ZERO,
                java.math.BigDecimal.TEN,
                java.math.BigDecimal.TEN),
            RotationDegrees.ZERO,
            NOW);
    ProjectAggregate project =
        new ProjectAggregate(
            metadata(), Optional.empty(), List.of(), List.of(grave), List.of(), List.of());
    Path image = temporaryDirectory.resolve("写真.png");
    ImageIO.write(new BufferedImage(4, 3, BufferedImage.TYPE_INT_RGB), "png", image.toFile());
    TestContext context = context(project, List.of(image));
    var selected =
        context
            .fileSelections()
            .start(
                "session-a",
                FileSelectionMode.MULTIPLE_FILES,
                FileSelectionPurpose.ATTACHMENT_IMPORT);

    Object result =
        context
            .service()
            .execute(
                PROJECT_ID,
                "session-a",
                0,
                CommandType.ADD_ATTACHMENTS,
                new CommandPayloads.AddAttachments(graveId.value(), selected.fileSelectionIds()));

    assertThat(result).isInstanceOf(EditingApiModels.CommandResponse.class);
    EditingApiModels.CommandResponse response = (EditingApiModels.CommandResponse) result;
    assertThat(response.upsertedAssets()).hasSize(1);
    UUID assetId = response.upsertedAssets().getFirst().assetId();
    assertThat(context.service().assetContent(PROJECT_ID, assetId).mediaType())
        .isEqualTo("image/png");
    assertThatThrownBy(() -> context.service().assetContent(PROJECT_ID, UUID.randomUUID()))
        .isInstanceOf(EditingApiException.class)
        .hasMessage("asset-not-found");
  }

  private TestContext context(ProjectAggregate project) throws Exception {
    return context(project, List.of());
  }

  private TestContext context(ProjectAggregate project, List<Path> selectedFiles) throws Exception {
    ClasspathJsonSchemaValidator schemas = new ClasspathJsonSchemaValidator();
    DefensiveJsonCodec codec = new DefensiveJsonCodec(schemas);
    ProjectFileV1Mapper mapper = new ProjectFileV1Mapper();
    ProjectRepository projects =
        new FileProjectRepository(codec, mapper, new ProjectAssetFileValidator());
    Path root = temporaryDirectory.resolve("project");
    Files.createDirectories(root);
    projects.write(root, project);
    OpenProjectManager openProjects = new OpenProjectManager();
    openProjects.open(PROJECT_ID, root, projects);
    java.util.ArrayDeque<UUID> ids = new java.util.ArrayDeque<>();
    for (int index = 0; index < 20; index++) {
      ids.add(UUID.randomUUID());
    }
    FileSelectionService fileSelections =
        new FileSelectionService(ignored -> selectedFiles, Clock.fixed(NOW, ZoneOffset.UTC));
    ProjectEditingApiService service =
        new ProjectEditingApiService(
            openProjects,
            new ProjectFingerprintCalculator(codec, mapper),
            Clock.fixed(NOW, ZoneOffset.UTC),
            ids::removeFirst,
            fileSelections);
    return new TestContext(service, openProjects, fileSelections);
  }

  private ProjectAggregate emptyProject() {
    return new ProjectAggregate(
        metadata(), Optional.empty(), List.of(), List.of(), List.of(), List.of());
  }

  private ProjectMetadata metadata() {
    return new ProjectMetadata(new ProjectId(PROJECT_ID), new ProjectName("テスト"), NOW, NOW);
  }

  private record TestContext(
      ProjectEditingApiService service,
      OpenProjectManager openProjects,
      FileSelectionService fileSelections)
      implements AutoCloseable {
    @Override
    public void close() {
      openProjects.close();
    }
  }
}
