package jp.hakamap.project.application.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jp.hakamap.infrastructure.persistence.schema.ClasspathJsonSchemaValidator;
import jp.hakamap.persistence.json.DefensiveJsonCodec;
import jp.hakamap.persistence.json.mapper.ProjectFileV1Mapper;
import jp.hakamap.persistence.json.repository.FileProjectRepository;
import jp.hakamap.persistence.json.repository.ProjectRepository;
import jp.hakamap.persistence.json.validation.ProjectAssetFileValidator;
import jp.hakamap.project.application.history.ProjectFingerprintCalculator;
import jp.hakamap.project.domain.model.ProjectAggregate;
import jp.hakamap.project.domain.model.ProjectMetadata;
import jp.hakamap.project.domain.value.ProjectId;
import jp.hakamap.project.domain.value.ProjectName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OpenProjectManagerTest {
  private static final UUID PROJECT_ID = UUID.fromString("2ded7f05-1b42-486a-ab7a-3ca0410f5e24");

  @TempDir Path temporaryDirectory;

  @Test
  void replacesWholeDirectoryAndRemovesAssetsAbsentFromReplacement() throws Exception {
    ProjectRepository repository = repository();
    Path root = temporaryDirectory.resolve("project");
    Path replacement = temporaryDirectory.resolve("replacement");
    repository.write(root, project("現在"));
    repository.write(replacement, project("復元"));
    Files.createDirectories(root.resolve("assets/attachments"));
    Files.writeString(root.resolve("assets/attachments/old.png"), "old");
    Files.createDirectories(replacement.resolve("assets/attachments"));
    Files.writeString(replacement.resolve("assets/attachments/new.png"), "new");

    try (OpenProjectManager manager = new OpenProjectManager()) {
      manager.open(PROJECT_ID, root, repository);
      manager.replaceProjectDirectory(PROJECT_ID, replacement, repository, fingerprints());

      assertThat(manager.current(PROJECT_ID).metadata().name().value()).isEqualTo("復元");
      assertThat(root.resolve("assets/attachments/new.png")).exists();
      assertThat(root.resolve("assets/attachments/old.png")).doesNotExist();
    }
  }

  @Test
  void restoresOriginalDirectoryWhenReplacementCannotBeLoaded() throws Exception {
    ProjectRepository repository = repository();
    Path root = temporaryDirectory.resolve("project");
    Path replacement = temporaryDirectory.resolve("replacement");
    repository.write(root, project("現在"));
    Files.createDirectories(replacement);
    Files.writeString(replacement.resolve("project.json"), "{invalid");
    String original = Files.readString(root.resolve("project.json"));

    try (OpenProjectManager manager = new OpenProjectManager()) {
      manager.open(PROJECT_ID, root, repository);

      assertThatThrownBy(
              () ->
                  manager.replaceProjectDirectory(
                      PROJECT_ID, replacement, repository, fingerprints()))
          .isInstanceOf(ProjectCatalogException.class)
          .hasMessage("backup-restore-failed");
      assertThat(Files.readString(root.resolve("project.json"))).isEqualTo(original);
      assertThat(manager.current(PROJECT_ID).metadata().name().value()).isEqualTo("現在");
    }
  }

  private ProjectRepository repository() {
    return new FileProjectRepository(
        codec(), new ProjectFileV1Mapper(), new ProjectAssetFileValidator());
  }

  private ProjectFingerprintCalculator fingerprints() {
    return new ProjectFingerprintCalculator(codec(), new ProjectFileV1Mapper());
  }

  private DefensiveJsonCodec codec() {
    return new DefensiveJsonCodec(new ClasspathJsonSchemaValidator());
  }

  private ProjectAggregate project(String name) {
    Instant created = Instant.parse("2026-01-01T00:00:00Z");
    return new ProjectAggregate(
        new ProjectMetadata(new ProjectId(PROJECT_ID), new ProjectName(name), created, created),
        Optional.empty(),
        List.of(),
        List.of(),
        List.of(),
        List.of());
  }
}
