package jp.hakamap.project.infrastructure.recovery;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import jp.hakamap.persistence.json.DefensiveJsonCodec;
import jp.hakamap.persistence.json.JsonDocumentType;
import jp.hakamap.persistence.json.mapper.ProjectFileV1Mapper;
import jp.hakamap.persistence.json.model.recovery.RecoveryFileV1;
import jp.hakamap.persistence.json.model.recovery.StagedAssetV1;
import jp.hakamap.persistence.json.validation.RecoveryFileV1Validator;
import jp.hakamap.project.application.history.ProjectEditingSession;
import jp.hakamap.project.application.history.ProjectFingerprintCalculator;
import jp.hakamap.project.domain.model.ProjectAggregate;
import jp.hakamap.project.domain.service.UuidSource;
import jp.hakamap.project.infrastructure.storage.StorageException;
import jp.hakamap.project.infrastructure.storage.StorageFileOperations;
import jp.hakamap.project.infrastructure.storage.StorageHashes;

public final class RecoverySnapshotService {
  private static final Duration INTERVAL = Duration.ofSeconds(30);

  private final StorageFileOperations files;

  private final DefensiveJsonCodec codec;

  private final ProjectFileV1Mapper mapper;

  private final RecoveryFileV1Validator validator;

  private final ProjectFingerprintCalculator fingerprints;

  private final Clock clock;

  private final UuidSource uuidSource;

  private final Path recoveryDirectory;

  private final Path temporaryAssetRoot;

  private final String applicationVersion;

  private final Map<UUID, Instant> lastSuccessfulWrites = new HashMap<>();

  public RecoverySnapshotService(
      StorageFileOperations files,
      DefensiveJsonCodec codec,
      ProjectFileV1Mapper mapper,
      RecoveryFileV1Validator validator,
      ProjectFingerprintCalculator fingerprints,
      Clock clock,
      UuidSource uuidSource,
      Path recoveryDirectory,
      Path temporaryAssetRoot,
      String applicationVersion) {
    this.files = files;
    this.codec = codec;
    this.mapper = mapper;
    this.validator = validator;
    this.fingerprints = fingerprints;
    this.clock = clock;
    this.uuidSource = uuidSource;
    this.recoveryDirectory = recoveryDirectory;
    this.temporaryAssetRoot = temporaryAssetRoot;
    this.applicationVersion = applicationVersion;
  }

  public RecoveryWriteResult writeIfDue(
      ProjectEditingSession session, List<StagedAssetV1> stagedAssets) {
    if (!session.dirty()) {
      return new RecoveryWriteResult(RecoveryWriteStatus.NOT_DIRTY, "not-dirty");
    }
    UUID projectId = session.current().metadata().id().value();
    Instant now = clock.instant();
    Instant last = lastSuccessfulWrites.get(projectId);
    if (last != null && Duration.between(last, now).compareTo(INTERVAL) < 0) {
      return new RecoveryWriteResult(RecoveryWriteStatus.NOT_DUE, "not-due");
    }
    RecoveryFileV1 recovery =
        new RecoveryFileV1(
            1,
            applicationVersion,
            projectId,
            now,
            session.baseProjectSha256(),
            mapper.toFile(session.current()),
            stagedAssets.stream()
                .sorted(Comparator.comparing(asset -> asset.assetId().toString()))
                .toList());
    Path target = recoveryDirectory.resolve(projectId + ".recovery.json");
    Path temporary =
        recoveryDirectory.resolve(".recovery-" + uuidSource.next().toString() + ".tmp");
    try {
      byte[] bytes = codec.write(recovery, JsonDocumentType.RECOVERY);
      validator.validate(recovery, temporaryAssetRoot);
      files.writeAndForce(temporary, bytes);
      codec.read(files.read(temporary), JsonDocumentType.RECOVERY, RecoveryFileV1.class);
      files.atomicMoveReplacing(temporary, target);
      RecoveryFileV1 committed =
          codec.read(files.read(target), JsonDocumentType.RECOVERY, RecoveryFileV1.class);
      validator.validate(committed, temporaryAssetRoot);
      lastSuccessfulWrites.put(projectId, now);
      return new RecoveryWriteResult(RecoveryWriteStatus.WRITTEN, "recovery-written");
    } catch (RuntimeException exception) {
      try {
        files.deleteIfExists(temporary);
      } catch (StorageException ignored) {
        // 次回起動時の既知一時ファイル清掃へ回す。
      }
      return new RecoveryWriteResult(RecoveryWriteStatus.FAILED, "recovery-write-failed");
    }
  }

  public RecoveryApplyResult apply(
      Path recoveryFile, Path formalProjectJson, ProjectAggregate formalProject) {
    try {
      byte[] formalBytes = files.read(formalProjectJson);
      RecoveryFileV1 recovery =
          codec.read(files.read(recoveryFile), JsonDocumentType.RECOVERY, RecoveryFileV1.class);
      validator.validate(recovery, temporaryAssetRoot);
      if (!recovery.projectId().equals(formalProject.metadata().id().value())
          || !recovery.baseProjectSha256().equals(StorageHashes.sha256(formalBytes))) {
        return new RecoveryApplyResult(
            RecoveryApplyStatus.BASE_MISMATCH, "recovery-base-mismatch", Optional.empty());
      }
      ProjectAggregate recovered = mapper.toDomain(recovery.projectSnapshot());
      ProjectEditingSession session =
          new ProjectEditingSession(
              recovered, formalProject, recovery.baseProjectSha256(), fingerprints);
      return new RecoveryApplyResult(
          RecoveryApplyStatus.APPLIED, "recovery-applied", Optional.of(session));
    } catch (RuntimeException exception) {
      return new RecoveryApplyResult(
          RecoveryApplyStatus.INVALID, "recovery-invalid", Optional.empty());
    }
  }
}
