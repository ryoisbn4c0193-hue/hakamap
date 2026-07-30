package jp.hakamap.project.application.transfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import jp.hakamap.infrastructure.persistence.schema.ClasspathJsonSchemaValidator;
import jp.hakamap.persistence.json.DefensiveJsonCodec;
import jp.hakamap.persistence.json.mapper.ProjectFileV1Mapper;
import jp.hakamap.persistence.json.repository.FileProjectRepository;
import jp.hakamap.persistence.json.repository.ProjectRepository;
import jp.hakamap.persistence.json.validation.ProjectAssetFileValidator;
import jp.hakamap.project.domain.model.ProjectAggregate;
import jp.hakamap.project.domain.model.ProjectMetadata;
import jp.hakamap.project.domain.value.ProjectId;
import jp.hakamap.project.domain.value.ProjectName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectArchiveServiceTest {
  private static final Instant NOW = Instant.parse("2026-07-30T01:02:03.004Z");

  @TempDir Path temporaryDirectory;

  @Test
  void createsVerifiesAndExtractsArchiveWithoutBackupRecursion() throws Exception {
    ProjectRepository repository = repository();
    Path root = temporaryDirectory.resolve("project");
    repository.write(root, project());
    Files.createDirectories(root.resolve("assets/attachments"));
    Files.writeString(root.resolve("assets/attachments/file.png"), "image");
    Files.createDirectories(root.resolve("backup/automatic"));
    Files.writeString(root.resolve("backup/automatic/ignored.zip"), "ignored");
    ProjectArchiveService service =
        new ProjectArchiveService(repository, Clock.fixed(NOW, ZoneOffset.UTC), "test");

    Path archive = service.exportArchive(root, temporaryDirectory.resolve("result.hakamap"));
    ProjectArchiveService.ArchiveInspection inspection = service.inspect(archive);
    Path extracted = temporaryDirectory.resolve("extracted");
    ProjectArchiveService.ExtractedProject result = service.extractAndValidate(archive, extracted);

    assertThat(inspection.projectId()).isEqualTo(project().metadata().id().value());
    assertThat(result.project().metadata().name().value()).isEqualTo("テスト");
    assertThat(extracted.resolve("assets/attachments/file.png")).exists();
    assertThat(extracted.resolve("backup")).doesNotExist();
  }

  @Test
  void createsOnlyOneAutomaticBackupPerLocalDate() {
    ProjectRepository repository = repository();
    Path root = temporaryDirectory.resolve("project");
    repository.write(root, project());
    ProjectArchiveService service =
        new ProjectArchiveService(repository, Clock.fixed(NOW, ZoneOffset.UTC), "test");

    Path first = service.createAutomaticBackup(root);
    Path second = service.createAutomaticBackup(root);

    assertThat(first).exists();
    assertThat(second).isNull();
  }

  @Test
  void rejectsParentTraversalBeforeExtraction() throws IOException {
    Path archive = temporaryDirectory.resolve("malicious.hakamap");
    try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
      output.putNextEntry(new ZipEntry("../outside.txt"));
      output.write(new byte[] {1});
      output.closeEntry();
    }
    ProjectArchiveService service =
        new ProjectArchiveService(repository(), Clock.fixed(NOW, ZoneOffset.UTC), "test");

    assertThatThrownBy(() -> service.extractAndValidate(archive, temporaryDirectory.resolve("out")))
        .isInstanceOf(ProjectTransferException.class)
        .hasMessage("archive-path-invalid");
    assertThat(temporaryDirectory.resolve("outside.txt")).doesNotExist();
  }

  @Test
  void rejectsDeclaredTotalSizeBeforeExtractionAndLeavesNoTemporaryDirectory() throws IOException {
    Path archive = temporaryDirectory.resolve("oversized.hakamap");
    String manifest =
        """
        {
          "formatVersion": 1,
          "archiveType": "export",
          "applicationVersion": "test",
          "createdAt": "2026-07-30T01:02:03.004Z",
          "projectId": "2ded7f05-1b42-486a-ab7a-3ca0410f5e24",
          "projectName": "テスト",
          "files": [{
            "path": "project.json",
            "sizeBytes": %d,
            "sha256": "unused"
          }]
        }
        """
            .formatted(ProjectArchiveService.MAX_TOTAL_UNCOMPRESSED_BYTES + 1);
    try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
      output.putNextEntry(new ZipEntry("manifest.json"));
      output.write(manifest.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      output.closeEntry();
      output.putNextEntry(new ZipEntry("project.json"));
      output.write(new byte[] {1});
      output.closeEntry();
    }
    ProjectArchiveService service =
        new ProjectArchiveService(repository(), Clock.fixed(NOW, ZoneOffset.UTC), "test");
    Path extracted = temporaryDirectory.resolve("oversized-out");

    assertThatThrownBy(() -> service.extractAndValidate(archive, extracted))
        .isInstanceOf(ProjectTransferException.class)
        .hasMessage("archive-total-size-exceeded");
    assertThat(extracted).doesNotExist();
  }

  private ProjectRepository repository() {
    return new FileProjectRepository(
        new DefensiveJsonCodec(new ClasspathJsonSchemaValidator()),
        new ProjectFileV1Mapper(),
        new ProjectAssetFileValidator());
  }

  private ProjectAggregate project() {
    Instant created = Instant.parse("2026-01-01T00:00:00Z");
    return new ProjectAggregate(
        new ProjectMetadata(
            new ProjectId(UUID.fromString("2ded7f05-1b42-486a-ab7a-3ca0410f5e24")),
            new ProjectName("テスト"),
            created,
            created),
        Optional.empty(),
        List.of(),
        List.of(),
        List.of(),
        List.of());
  }
}
