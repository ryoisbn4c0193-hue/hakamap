package jp.hakamap.project.infrastructure.storage;

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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import jp.hakamap.persistence.json.DefensiveJsonCodec;
import jp.hakamap.persistence.json.PersistenceTestFixtures;
import jp.hakamap.persistence.json.mapper.ProjectFileV1Mapper;
import jp.hakamap.persistence.json.model.catalog.ActiveCatalogProjectV1;
import jp.hakamap.persistence.json.model.catalog.CatalogFileV1;
import jp.hakamap.persistence.json.repository.FileProjectRepository;
import jp.hakamap.persistence.json.validation.CatalogFileV1Validator;
import jp.hakamap.persistence.json.validation.ProjectAssetFileValidator;
import jp.hakamap.project.application.history.CommandId;
import jp.hakamap.project.application.history.CommandType;
import jp.hakamap.project.application.history.EntityDelta;
import jp.hakamap.project.application.history.ProjectChangeSet;
import jp.hakamap.project.application.history.ProjectEditingSession;
import jp.hakamap.project.application.history.ProjectFingerprintCalculator;
import jp.hakamap.project.application.history.ValueDelta;
import jp.hakamap.project.domain.model.AssetMetadata;
import jp.hakamap.project.domain.value.AssetId;
import jp.hakamap.project.domain.value.AssetType;
import jp.hakamap.project.domain.value.ProjectName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectStorageTransactionCoordinatorTest {
  private final DefensiveJsonCodec codec = PersistenceTestFixtures.codec();

  private final ProjectFileV1Mapper mapper = new ProjectFileV1Mapper();

  private final ProjectAssetFileValidator assetValidator = new ProjectAssetFileValidator();

  private final ProjectFingerprintCalculator fingerprints =
      new ProjectFingerprintCalculator(codec, mapper);

  @TempDir Path temporaryDirectory;

  @Test
  void commitsAtomicallyAndKeepsRevisionAndHistory() throws Exception {
    Path root = initializedProject();
    ProjectEditingSession session = dirtySession(root);
    Path recovery = temporaryDirectory.resolve("recovery.json");
    Files.writeString(recovery, "recovery");
    ProjectStorageTransactionCoordinator coordinator = coordinator(new NioStorageFileOperations());

    try (ProjectFileLock lock = ProjectFileLock.acquire(root)) {
      SaveResult result = coordinator.save(root, session, 1, lock, List.of(), recovery);

      assertThat(result.status()).isEqualTo(CommitStatus.COMMITTED);
      assertThat(session.revision()).isEqualTo(1);
      assertThat(session.undoSize()).isEqualTo(1);
      assertThat(session.dirty()).isFalse();
      assertThat(session.current().metadata().updatedAt())
          .isEqualTo(Instant.parse("2026-02-03T04:05:06.007Z"));
      assertThat(Files.exists(recovery)).isFalse();
      assertThat(coordinator.save(root, session, 1, lock, List.of(), recovery).status())
          .isEqualTo(CommitStatus.NO_CHANGES);
    }
  }

  @Test
  void rejectsExternalModificationBeforeWriting() throws Exception {
    Path root = initializedProject();
    ProjectEditingSession session = dirtySession(root);
    Files.writeString(
        root.resolve("project.json"), Files.readString(root.resolve("project.json")) + " ");

    try (ProjectFileLock lock = ProjectFileLock.acquire(root)) {
      SaveResult result =
          coordinator(new NioStorageFileOperations()).save(root, session, 1, lock, List.of(), null);

      assertThat(result.status()).isEqualTo(CommitStatus.NOT_COMMITTED);
      assertThat(result.code()).isEqualTo("project-externally-modified");
      assertThat(session.dirty()).isTrue();
    }
  }

  @Test
  void keepsOldJsonWhenAtomicMoveFailsBeforeCommit() throws Exception {
    Path root = initializedProject();
    byte[] oldJson = Files.readAllBytes(root.resolve("project.json"));
    ProjectEditingSession session = dirtySession(root);
    StorageFileOperations operations =
        new ForwardingOperations(new NioStorageFileOperations()) {
          @Override
          public void atomicMoveReplacing(Path source, Path target) {
            throw new StorageException("injected-before-move");
          }
        };

    try (ProjectFileLock lock = ProjectFileLock.acquire(root)) {
      SaveResult result = coordinator(operations).save(root, session, 1, lock, List.of(), null);

      assertThat(result.status()).isEqualTo(CommitStatus.NOT_COMMITTED);
      assertThat(Files.readAllBytes(root.resolve("project.json"))).isEqualTo(oldJson);
      assertThat(session.editingStopped()).isFalse();
    }
  }

  @Test
  void stopsEditingWhenCommitOutcomeCannotBeRead() throws Exception {
    Path root = initializedProject();
    ProjectEditingSession session = dirtySession(root);
    AtomicBoolean disconnected = new AtomicBoolean(true);
    StorageFileOperations operations =
        new ForwardingOperations(new NioStorageFileOperations()) {
          private Path committedTarget;

          @Override
          public void atomicMoveReplacing(Path source, Path target) {
            delegate.atomicMoveReplacing(source, target);
            committedTarget = target;
            throw new StorageException("injected-after-move");
          }

          @Override
          public byte[] read(Path path) {
            if (disconnected.get() && path.equals(committedTarget)) {
              throw new StorageException("injected-disconnect");
            }
            return delegate.read(path);
          }
        };
    ProjectStorageTransactionCoordinator coordinator = coordinator(operations);

    try (ProjectFileLock lock = ProjectFileLock.acquire(root)) {
      SaveResult result = coordinator.save(root, session, 1, lock, List.of(), null);

      assertThat(result.status()).isEqualTo(CommitStatus.COMMIT_OUTCOME_UNKNOWN);
      assertThat(session.editingStopped()).isTrue();
      assertThatThrownBy(() -> session.undo(1)).hasMessage("editing-stopped");
      disconnected.set(false);
      assertThat(coordinator.resolveUnknownOutcome(root, session, lock))
          .isEqualTo(OutcomeResolution.COMMITTED);
      assertThat(session.editingStopped()).isFalse();
      assertThat(session.dirty()).isFalse();
    }
  }

  @Test
  void retainsPlacedAssetUntilUnknownCommitIsResolved() throws Exception {
    Path root = initializedProject();
    String baseSha = StorageHashes.sha256(Files.readAllBytes(root.resolve("project.json")));
    ProjectEditingSession session =
        new ProjectEditingSession(PersistenceTestFixtures.emptyProject(), baseSha, fingerprints);
    UUID assetUuid = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
    byte[] png = new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0, 0, 0, 0};
    Path stagedPath = temporaryDirectory.resolve("unknown-outcome-staged.png");
    Files.write(stagedPath, png);
    AssetMetadata metadata =
        new AssetMetadata(
            new AssetId(assetUuid),
            AssetType.BACKGROUND,
            Optional.empty(),
            "background.png",
            "assets/backgrounds/" + assetUuid + ".png",
            "image/png",
            "image/png",
            png.length,
            StorageHashes.sha256(png),
            PersistenceTestFixtures.CREATED_AT,
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty());
    session.apply(
        0,
        new ProjectChangeSet(
            new CommandId(UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb")),
            CommandType.SET_BACKGROUND,
            PersistenceTestFixtures.CREATED_AT,
            List.of(),
            List.of(),
            List.of(),
            List.of(EntityDelta.created(new AssetId(assetUuid), metadata)),
            Optional.empty(),
            Optional.empty(),
            Set.of()));
    AtomicBoolean disconnected = new AtomicBoolean(true);
    StorageFileOperations operations =
        new ForwardingOperations(new NioStorageFileOperations()) {
          private Path committedTarget;

          @Override
          public void atomicMoveReplacing(Path source, Path target) {
            delegate.atomicMoveReplacing(source, target);
            committedTarget = target;
            throw new StorageException("injected-after-move");
          }

          @Override
          public byte[] read(Path path) {
            if (disconnected.get() && path.equals(committedTarget)) {
              throw new StorageException("injected-disconnect");
            }
            return delegate.read(path);
          }
        };
    ProjectStorageTransactionCoordinator coordinator = coordinator(operations);
    Path placedAsset = root.resolve(metadata.relativePath());

    try (ProjectFileLock lock = ProjectFileLock.acquire(root)) {
      SaveResult result =
          coordinator.save(
              root, session, 1, lock, List.of(new StagedAsset(stagedPath, metadata)), null);

      assertThat(result.status()).isEqualTo(CommitStatus.COMMIT_OUTCOME_UNKNOWN);
      assertThat(Files.readAllBytes(placedAsset)).isEqualTo(png);
      assertThat(Files.exists(stagedPath)).isTrue();

      disconnected.set(false);
      assertThat(coordinator.resolveUnknownOutcome(root, session, lock))
          .isEqualTo(OutcomeResolution.COMMITTED);
      assertThat(Files.readAllBytes(placedAsset)).isEqualTo(png);
      assertThat(Files.exists(stagedPath)).isFalse();
    }
  }

  @Test
  void rejectsInsufficientSpaceAndDuplicateLock() throws Exception {
    Path root = initializedProject();
    ProjectEditingSession session = dirtySession(root);
    StorageFileOperations noSpace =
        new ForwardingOperations(new NioStorageFileOperations()) {
          @Override
          public long usableSpace(Path path) {
            return 0;
          }
        };

    try (ProjectFileLock lock = ProjectFileLock.acquire(root)) {
      assertThatThrownBy(() -> ProjectFileLock.acquire(root))
          .isInstanceOfSatisfying(
              StorageException.class,
              exception -> assertThat(exception.code()).isEqualTo("project-already-locked"));
      SaveResult result = coordinator(noSpace).save(root, session, 1, lock, List.of(), null);
      assertThat(result.code()).isEqualTo("storage-space-insufficient");
      assertThat(session.dirty()).isTrue();
    }
  }

  @Test
  void commitsCatalogAndKeepsValidatedBackup() {
    AtomicInteger sequence = new AtomicInteger();
    CatalogStorageTransaction transaction =
        new CatalogStorageTransaction(
            new NioStorageFileOperations(),
            codec,
            new CatalogFileV1Validator(),
            () ->
                UUID.fromString(
                    "44444444-4444-4444-8444-"
                        + String.format("%012d", sequence.incrementAndGet())));
    Path catalogFile = temporaryDirectory.resolve("catalog.json");
    CatalogFileV1 empty = new CatalogFileV1(1, null, List.of());
    CatalogFileV1 populated =
        new CatalogFileV1(
            1,
            PersistenceTestFixtures.PROJECT_ID,
            List.of(
                new ActiveCatalogProjectV1(
                    PersistenceTestFixtures.PROJECT_ID,
                    "C:\\墓地",
                    "墓地",
                    PersistenceTestFixtures.CREATED_AT,
                    PersistenceTestFixtures.CREATED_AT,
                    "active")));

    assertThat(transaction.write(catalogFile, empty).status()).isEqualTo(CommitStatus.COMMITTED);
    assertThat(transaction.write(catalogFile, populated).status())
        .isEqualTo(CommitStatus.COMMITTED);

    var backup =
        codec.read(
            new NioStorageFileOperations().read(temporaryDirectory.resolve("catalog.json.bak")),
            jp.hakamap.persistence.json.JsonDocumentType.CATALOG,
            CatalogFileV1.class);
    assertThat(backup).isEqualTo(empty);
  }

  @Test
  void placesValidatedStagedAssetBeforeJsonCommit() throws Exception {
    Path root = initializedProject();
    String baseSha = StorageHashes.sha256(Files.readAllBytes(root.resolve("project.json")));
    ProjectEditingSession session =
        new ProjectEditingSession(PersistenceTestFixtures.emptyProject(), baseSha, fingerprints);
    UUID assetUuid = UUID.fromString("55555555-5555-4555-8555-555555555555");
    byte[] png = new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0, 0, 0, 0};
    Path stagedPath = temporaryDirectory.resolve("staged.png");
    Files.write(stagedPath, png);
    AssetMetadata metadata =
        new AssetMetadata(
            new AssetId(assetUuid),
            AssetType.BACKGROUND,
            Optional.empty(),
            "background.png",
            "assets/backgrounds/" + assetUuid + ".png",
            "image/png",
            "image/png",
            png.length,
            StorageHashes.sha256(png),
            PersistenceTestFixtures.CREATED_AT,
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty());
    session.apply(
        0,
        new ProjectChangeSet(
            new CommandId(UUID.fromString("66666666-6666-4666-8666-666666666666")),
            CommandType.SET_BACKGROUND,
            PersistenceTestFixtures.CREATED_AT,
            List.of(),
            List.of(),
            List.of(),
            List.of(EntityDelta.created(new AssetId(assetUuid), metadata)),
            Optional.empty(),
            Optional.empty(),
            Set.of()));

    try (ProjectFileLock lock = ProjectFileLock.acquire(root)) {
      SaveResult result =
          coordinator(new NioStorageFileOperations())
              .save(root, session, 1, lock, List.of(new StagedAsset(stagedPath, metadata)), null);

      assertThat(result.status()).isEqualTo(CommitStatus.COMMITTED);
      assertThat(Files.exists(stagedPath)).isFalse();
      assertThat(Files.readAllBytes(root.resolve(metadata.relativePath()))).isEqualTo(png);
    }
  }

  private Path initializedProject() {
    Path root = temporaryDirectory.resolve(UUID.randomUUID().toString());
    new FileProjectRepository(codec, mapper, assetValidator)
        .write(root, PersistenceTestFixtures.emptyProject());
    return root;
  }

  private ProjectEditingSession dirtySession(Path root) throws Exception {
    String baseSha = StorageHashes.sha256(Files.readAllBytes(root.resolve("project.json")));
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
            Optional.of(ValueDelta.changed(new ProjectName("テスト墓地"), new ProjectName("保存後墓地"))),
            Optional.empty(),
            Set.of()));
    return session;
  }

  private ProjectStorageTransactionCoordinator coordinator(StorageFileOperations operations) {
    AtomicInteger sequence = new AtomicInteger();
    return new ProjectStorageTransactionCoordinator(
        operations,
        codec,
        mapper,
        assetValidator,
        fingerprints,
        Clock.fixed(Instant.parse("2026-02-03T04:05:06.007Z"), ZoneOffset.UTC),
        () ->
            UUID.fromString(
                "33333333-3333-4333-8333-" + String.format("%012d", sequence.incrementAndGet())));
  }

  private abstract static class ForwardingOperations implements StorageFileOperations {
    protected final StorageFileOperations delegate;

    private ForwardingOperations(StorageFileOperations delegate) {
      this.delegate = delegate;
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
