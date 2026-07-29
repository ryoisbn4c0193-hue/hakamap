package jp.hakamap.project.infrastructure.recovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import jp.hakamap.persistence.json.JsonDocumentType;
import jp.hakamap.persistence.json.PersistenceTestFixtures;
import jp.hakamap.persistence.json.mapper.ProjectFileV1Mapper;
import jp.hakamap.persistence.json.model.recovery.RecoveryFileV1;
import jp.hakamap.persistence.json.model.recovery.StagedAssetV1;
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
import jp.hakamap.project.infrastructure.storage.StorageException;
import jp.hakamap.project.infrastructure.storage.StorageFileOperations;
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

  @Test
  void retriesIdempotentDiscardAfterSecondAssetDeletionFailsOnce() throws Exception {
    UUID firstId = UUID.fromString("44444444-4444-4444-8444-444444444444");
    UUID secondId = UUID.fromString("55555555-5555-4555-8555-555555555555");
    Path temporaryAssets = temporaryDirectory.resolve("temp-assets");
    Path projectTemporaryRoot =
        temporaryAssets.resolve(PersistenceTestFixtures.PROJECT_ID.toString());
    Files.createDirectories(projectTemporaryRoot);
    Path first = projectTemporaryRoot.resolve(firstId + ".png");
    Path second = projectTemporaryRoot.resolve(secondId + ".png");
    Files.write(first, new byte[] {1});
    Files.write(second, new byte[] {2});
    Path recoveryFile =
        temporaryDirectory
            .resolve("recovery")
            .resolve(PersistenceTestFixtures.PROJECT_ID + ".recovery.json");
    Files.createDirectories(recoveryFile.getParent());
    RecoveryFileV1 recovery =
        new RecoveryFileV1(
            1,
            "0.0.1",
            PersistenceTestFixtures.PROJECT_ID,
            Instant.parse("2026-02-03T04:05:06.007Z"),
            "0".repeat(64),
            PersistenceTestFixtures.emptyProjectFile(),
            List.of(
                new StagedAssetV1(
                    firstId,
                    PersistenceTestFixtures.PROJECT_ID + "/" + firstId + ".png",
                    1,
                    "0".repeat(64)),
                new StagedAssetV1(
                    secondId,
                    PersistenceTestFixtures.PROJECT_ID + "/" + secondId + ".png",
                    1,
                    "0".repeat(64))));
    Files.write(recoveryFile, codec.write(recovery, JsonDocumentType.RECOVERY));

    StorageFileOperations injected = new FailingDeleteFileOperations(second);
    RecoverySnapshotService service = service(injected);

    assertThatThrownBy(() -> service.discard(PersistenceTestFixtures.PROJECT_ID))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("recovery-asset-cleanup-failed");
    assertThat(first).doesNotExist();
    assertThat(second).exists();
    assertThat(recoveryFile).exists();

    service.discard(PersistenceTestFixtures.PROJECT_ID);
    assertThat(first).doesNotExist();
    assertThat(second).doesNotExist();
    assertThat(projectTemporaryRoot).doesNotExist();
    assertThat(recoveryFile).doesNotExist();
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
    return service(new NioStorageFileOperations());
  }

  private RecoverySnapshotService service(StorageFileOperations files) {
    AtomicInteger sequence = new AtomicInteger();
    return new RecoverySnapshotService(
        files,
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

  private static final class FailingDeleteFileOperations implements StorageFileOperations {
    private final NioStorageFileOperations delegate = new NioStorageFileOperations();

    private final Path failedPath;

    private boolean failed;

    private FailingDeleteFileOperations(Path failedPath) {
      this.failedPath = failedPath;
    }

    @Override
    public byte[] read(Path path) {
      return delegate.read(path);
    }

    @Override
    public void writeAndForce(Path path, byte[] bytes) {
      delegate.writeAndForce(path, bytes);
    }

    @Override
    public void copyAndForce(Path source, Path target) {
      delegate.copyAndForce(source, target);
    }

    @Override
    public void atomicMoveReplacing(Path source, Path target) {
      delegate.atomicMoveReplacing(source, target);
    }

    @Override
    public void atomicMoveNew(Path source, Path target) {
      delegate.atomicMoveNew(source, target);
    }

    @Override
    public boolean exists(Path path) {
      return delegate.exists(path);
    }

    @Override
    public void deleteIfExists(Path path) {
      if (!failed && path.equals(failedPath)) {
        failed = true;
        throw new StorageException("injected-delete-failure");
      }
      delegate.deleteIfExists(path);
    }

    @Override
    public long usableSpace(Path path) {
      return delegate.usableSpace(path);
    }

    @Override
    public List<Path> list(Path directory) {
      return delegate.list(directory);
    }
  }
}
