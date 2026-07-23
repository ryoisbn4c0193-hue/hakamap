package jp.hakamap.project.infrastructure.recovery;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import jp.hakamap.persistence.json.DefensiveJsonCodec;
import jp.hakamap.persistence.json.PersistenceTestFixtures;
import jp.hakamap.persistence.json.mapper.ProjectFileV1Mapper;
import jp.hakamap.persistence.json.repository.FileProjectRepository;
import jp.hakamap.persistence.json.validation.ProjectAssetFileValidator;
import jp.hakamap.persistence.json.validation.RecoveryFileV1Validator;
import jp.hakamap.project.application.history.CommandId;
import jp.hakamap.project.application.history.CommandType;
import jp.hakamap.project.application.history.ProjectChangeSet;
import jp.hakamap.project.application.history.ProjectEditingSession;
import jp.hakamap.project.application.history.ProjectFingerprintCalculator;
import jp.hakamap.project.application.history.ValueDelta;
import jp.hakamap.project.domain.value.ProjectName;
import jp.hakamap.project.infrastructure.storage.NioStorageFileOperations;
import jp.hakamap.project.infrastructure.storage.StorageHashes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RecoverySnapshotServiceTest {
  private final DefensiveJsonCodec codec = PersistenceTestFixtures.codec();

  private final ProjectFileV1Mapper mapper = new ProjectFileV1Mapper();

  private final ProjectFingerprintCalculator fingerprints =
      new ProjectFingerprintCalculator(codec, mapper);

  @TempDir Path temporaryDirectory;

  @Test
  void writesAtMostOncePerIntervalAndAppliesAsDirtySession() throws Exception {
    Path projectRoot = temporaryDirectory.resolve("project");
    Path projectJson = projectRoot.resolve("project.json");
    new FileProjectRepository(codec, mapper, new ProjectAssetFileValidator())
        .write(projectRoot, PersistenceTestFixtures.emptyProject());
    String baseSha = StorageHashes.sha256(Files.readAllBytes(projectJson));
    ProjectEditingSession editing = dirtySession(baseSha);
    RecoverySnapshotService service = service();

    RecoveryWriteResult first = service.writeIfDue(editing, List.of());
    RecoveryWriteResult second = service.writeIfDue(editing, List.of());
    Path recoveryFile =
        temporaryDirectory
            .resolve("recovery")
            .resolve(PersistenceTestFixtures.PROJECT_ID + ".recovery.json");

    assertThat(first.status()).isEqualTo(RecoveryWriteStatus.WRITTEN);
    assertThat(second.status()).isEqualTo(RecoveryWriteStatus.NOT_DUE);
    assertThat(Files.isRegularFile(recoveryFile)).isTrue();
    RecoveryApplyResult applied =
        service.apply(recoveryFile, projectJson, PersistenceTestFixtures.emptyProject());
    assertThat(applied.status()).isEqualTo(RecoveryApplyStatus.APPLIED);
    ProjectEditingSession recovered = applied.session().orElseThrow();
    assertThat(recovered.revision()).isZero();
    assertThat(recovered.undoSize()).isZero();
    assertThat(recovered.redoSize()).isZero();
    assertThat(recovered.dirty()).isTrue();
    assertThat(recovered.current().metadata().name().value()).isEqualTo("復旧名");
    assertThat(Files.readAllBytes(projectJson))
        .isEqualTo(
            codec.write(
                PersistenceTestFixtures.emptyProjectFile(),
                jp.hakamap.persistence.json.JsonDocumentType.PROJECT));
  }

  @Test
  void rejectsBaseMismatchAndBrokenRecoveryWithoutChangingProject() throws Exception {
    Path projectRoot = temporaryDirectory.resolve("other-project");
    Path projectJson = projectRoot.resolve("project.json");
    new FileProjectRepository(codec, mapper, new ProjectAssetFileValidator())
        .write(projectRoot, PersistenceTestFixtures.emptyProject());
    String baseSha = StorageHashes.sha256(Files.readAllBytes(projectJson));
    ProjectEditingSession editing = dirtySession(baseSha);
    RecoverySnapshotService service = service();
    service.writeIfDue(editing, List.of());
    Path recoveryFile =
        temporaryDirectory
            .resolve("recovery")
            .resolve(PersistenceTestFixtures.PROJECT_ID + ".recovery.json");

    Files.writeString(projectJson, Files.readString(projectJson) + " ");
    assertThat(
            service
                .apply(recoveryFile, projectJson, PersistenceTestFixtures.emptyProject())
                .status())
        .isEqualTo(RecoveryApplyStatus.BASE_MISMATCH);
    Files.writeString(recoveryFile, "{broken");
    assertThat(
            service
                .apply(recoveryFile, projectJson, PersistenceTestFixtures.emptyProject())
                .status())
        .isEqualTo(RecoveryApplyStatus.INVALID);
  }

  @Test
  void skipsRecoveryWhenSessionIsNotDirty() {
    ProjectEditingSession clean =
        new ProjectEditingSession(
            PersistenceTestFixtures.emptyProject(), "0".repeat(64), fingerprints);

    assertThat(service().writeIfDue(clean, List.of()).status())
        .isEqualTo(RecoveryWriteStatus.NOT_DIRTY);
  }

  private ProjectEditingSession dirtySession(String baseSha) {
    ProjectEditingSession session =
        new ProjectEditingSession(PersistenceTestFixtures.emptyProject(), baseSha, fingerprints);
    session.apply(
        0,
        new ProjectChangeSet(
            new CommandId(UUID.fromString("22222222-2222-4222-8222-222222222222")),
            CommandType.RENAME_PROJECT,
            Instant.parse("2026-01-02T03:04:05.006Z"),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            Optional.of(ValueDelta.changed(new ProjectName("テスト墓地"), new ProjectName("復旧名"))),
            Optional.empty(),
            Set.of()));
    return session;
  }

  private RecoverySnapshotService service() {
    AtomicInteger sequence = new AtomicInteger();
    return new RecoverySnapshotService(
        new NioStorageFileOperations(),
        codec,
        mapper,
        new RecoveryFileV1Validator(),
        fingerprints,
        Clock.fixed(Instant.parse("2026-02-03T04:05:06.007Z"), ZoneOffset.UTC),
        () ->
            UUID.fromString(
                "33333333-3333-4333-8333-" + String.format("%012d", sequence.incrementAndGet())),
        temporaryDirectory.resolve("recovery"),
        temporaryDirectory.resolve("temp-assets"),
        "0.0.1");
  }
}
