package jp.hakamap.project.application.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import jp.hakamap.infrastructure.fileselection.FileSelectionPurpose;
import jp.hakamap.infrastructure.fileselection.FileSelectionService;
import jp.hakamap.infrastructure.persistence.schema.ClasspathJsonSchemaValidator;
import jp.hakamap.persistence.json.DefensiveJsonCodec;
import jp.hakamap.persistence.json.mapper.ProjectFileV1Mapper;
import jp.hakamap.persistence.json.model.catalog.CatalogFileV1;
import jp.hakamap.persistence.json.repository.CatalogRepository;
import jp.hakamap.persistence.json.repository.FileProjectRepository;
import jp.hakamap.persistence.json.repository.ProjectRepository;
import jp.hakamap.persistence.json.validation.ProjectAssetFileValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectCatalogServiceTest {
  private static final Instant NOW = Instant.parse("2026-07-29T01:02:03Z");

  @TempDir Path temporaryDirectory;

  @Test
  void createsListsOpensClosesTrashesRestoresAndDeletesProject() throws IOException {
    Path parent = Files.createDirectory(temporaryDirectory.resolve("projects"));
    FileSelectionService selections =
        new FileSelectionService(ignored -> List.of(parent), Clock.fixed(NOW, ZoneOffset.UTC));
    UUID selectionId =
        selections
            .start(
                "session",
                jp.hakamap.infrastructure.fileselection.FileSelectionMode.DIRECTORY,
                FileSelectionPurpose.PROJECT_CREATE_DIRECTORY)
            .fileSelectionIds()
            .getFirst();
    UUID projectId = UUID.fromString("9cdef6d7-e4eb-4b9f-8967-687eedff741c");
    MemoryCatalogRepository catalogs =
        new MemoryCatalogRepository(temporaryDirectory.resolve("catalog.json"));
    ProjectCatalogService service = service(catalogs, selections, () -> projectId);

    var created = service.create("session", selectionId, "中央墓地");

    assertThat(created.projectId()).isEqualTo(projectId);
    assertThat(created.locationLabel()).isEqualTo("hakamap-project-" + projectId);
    assertThat(created.available()).isTrue();
    assertThat(service.list().projects()).hasSize(1);
    assertThat(service.open(projectId).name()).isEqualTo("中央墓地");
    assertThat(service.list().openProjectId()).isEqualTo(projectId);
    assertThat(service.close(projectId, "discard", "session").status()).isEqualTo("closed");

    var trashed = service.trash(projectId);
    assertThat(trashed.state()).isEqualTo("trashed");
    assertThat(service.list().projects().getFirst().locationLabel())
        .isEqualTo(projectId.toString());

    var restored = service.restore(projectId, "session", null);
    assertThat(restored.state()).isEqualTo("active");
    assertThat(restored.available()).isTrue();

    service.trash(projectId);
    service.permanentlyDelete(projectId);
    assertThat(service.list().projects()).isEmpty();
  }

  @Test
  void defaultRequiresAvailableActiveProjectAndOpenPreventsAnotherProject() throws IOException {
    Path parent = Files.createDirectory(temporaryDirectory.resolve("projects"));
    FileSelectionService selections =
        new FileSelectionService(ignored -> List.of(parent), Clock.fixed(NOW, ZoneOffset.UTC));
    UUID firstId = UUID.fromString("9cdef6d7-e4eb-4b9f-8967-687eedff741c");
    UUID secondId = UUID.fromString("7ad0828f-5220-40b5-ae39-c193bb5d3564");
    java.util.ArrayDeque<UUID> ids = new java.util.ArrayDeque<>(List.of(firstId, secondId));
    ProjectCatalogService service =
        service(
            new MemoryCatalogRepository(temporaryDirectory.resolve("catalog.json")),
            selections,
            ids::removeFirst);

    UUID firstSelection = selection(selections);
    service.create("session", firstSelection, "第一");
    UUID secondSelection = selection(selections);
    service.create("session", secondSelection, "第二");
    assertThat(service.setDefault(firstId).defaultProject()).isTrue();

    service.open(firstId);
    assertThatThrownBy(() -> service.open(secondId))
        .isInstanceOf(ProjectCatalogException.class)
        .hasMessage("project-busy");
  }

  private UUID selection(FileSelectionService selections) {
    return selections
        .start(
            "session",
            jp.hakamap.infrastructure.fileselection.FileSelectionMode.DIRECTORY,
            FileSelectionPurpose.PROJECT_CREATE_DIRECTORY)
        .fileSelectionIds()
        .getFirst();
  }

  private ProjectCatalogService service(
      MemoryCatalogRepository catalogs,
      FileSelectionService selections,
      jp.hakamap.project.domain.service.UuidSource uuids) {
    DefensiveJsonCodec codec = new DefensiveJsonCodec(new ClasspathJsonSchemaValidator());
    ProjectRepository projects =
        new FileProjectRepository(
            codec, new ProjectFileV1Mapper(), new ProjectAssetFileValidator());
    return new ProjectCatalogService(
        new CatalogPaths(catalogs.file),
        catalogs,
        catalogs::write,
        projects,
        selections,
        new OpenProjectManager(),
        Clock.fixed(NOW, ZoneOffset.UTC),
        uuids);
  }

  private static final class MemoryCatalogRepository implements CatalogRepository {
    private final Path file;

    private CatalogFileV1 catalog;

    private MemoryCatalogRepository(Path file) {
      this.file = file;
    }

    @Override
    public CatalogFileV1 read(Path ignored) {
      return catalog;
    }

    @Override
    public void write(Path ignored, CatalogFileV1 value) {
      catalog = value;
      try {
        Files.writeString(file, "catalog");
      } catch (IOException exception) {
        throw new IllegalStateException(exception);
      }
    }
  }
}
