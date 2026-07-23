package jp.hakamap.project.infrastructure.storage;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import jp.hakamap.persistence.json.DefensiveJsonCodec;
import jp.hakamap.persistence.json.JsonDocumentType;
import jp.hakamap.persistence.json.mapper.ProjectFileV1Mapper;
import jp.hakamap.persistence.json.model.project.ProjectFileV1;
import jp.hakamap.persistence.json.validation.ProjectAssetFileValidator;
import jp.hakamap.project.application.history.EditingSessionException;
import jp.hakamap.project.application.history.ProjectEditingSession;
import jp.hakamap.project.application.history.ProjectFingerprintCalculator;
import jp.hakamap.project.domain.model.ProjectAggregate;
import jp.hakamap.project.domain.model.ProjectMetadata;
import jp.hakamap.project.domain.service.UuidSource;

public final class ProjectStorageTransactionCoordinator {
  private final ReentrantLock transactionLock = new ReentrantLock();

  private final StorageFileOperations files;

  private final DefensiveJsonCodec codec;

  private final ProjectFileV1Mapper mapper;

  private final ProjectAssetFileValidator assetValidator;

  private final ProjectFingerprintCalculator fingerprints;

  private final Clock clock;

  private final UuidSource uuidSource;

  private Optional<PendingCommit> pendingCommit = Optional.empty();

  public ProjectStorageTransactionCoordinator(
      StorageFileOperations files,
      DefensiveJsonCodec codec,
      ProjectFileV1Mapper mapper,
      ProjectAssetFileValidator assetValidator,
      ProjectFingerprintCalculator fingerprints,
      Clock clock,
      UuidSource uuidSource) {
    this.files = files;
    this.codec = codec;
    this.mapper = mapper;
    this.assetValidator = assetValidator;
    this.fingerprints = fingerprints;
    this.clock = clock;
    this.uuidSource = uuidSource;
  }

  public SaveResult save(
      Path projectRoot,
      ProjectEditingSession session,
      long expectedRevision,
      ProjectFileLock projectLock,
      List<StagedAsset> stagedAssets,
      Path recoveryFile) {
    transactionLock.lock();
    List<Path> newlyPlacedAssets = new ArrayList<>();
    Path temporaryJson = null;
    try {
      session.requireRevision(expectedRevision);
      if (!session.dirty()) {
        return SaveResult.noChanges();
      }
      requireLock(projectLock);
      Path projectJson = projectRoot.resolve("project.json");
      byte[] oldJson = files.read(projectJson);
      String oldSha256 = StorageHashes.sha256(oldJson);
      if (!oldSha256.equals(session.baseProjectSha256())) {
        return SaveResult.notCommitted("project-externally-modified");
      }
      ProjectAggregate candidate = withUpdatedAt(session.current(), clock.instant());
      byte[] candidateJson = codec.write(mapper.toFile(candidate), JsonDocumentType.PROJECT);
      requireCapacity(projectRoot, candidateJson.length, stagedAssets);
      placeStagedAssets(projectRoot, stagedAssets, newlyPlacedAssets);
      assetValidator.validate(projectRoot, candidate);

      temporaryJson = projectRoot.resolve(".project-" + uuidSource.next().toString() + ".tmp");
      files.writeAndForce(temporaryJson, candidateJson);
      ProjectFileV1 reread =
          codec.read(files.read(temporaryJson), JsonDocumentType.PROJECT, ProjectFileV1.class);
      mapper.toDomain(reread);
      String candidateSha256 = StorageHashes.sha256(candidateJson);

      requireLock(projectLock);
      if (!StorageHashes.sha256(files.read(projectJson)).equals(oldSha256)) {
        cleanupBeforeCommit(temporaryJson, newlyPlacedAssets);
        return SaveResult.notCommitted("project-externally-modified");
      }
      SaveResult commitResult =
          commitJson(projectJson, temporaryJson, oldSha256, candidateSha256, candidate, session);
      if (commitResult.status() == CommitStatus.NOT_COMMITTED) {
        cleanupBeforeCommit(temporaryJson, newlyPlacedAssets);
        temporaryJson = null;
        return commitResult;
      }
      if (commitResult.status() == CommitStatus.COMMIT_OUTCOME_UNKNOWN) {
        pendingCommit =
            Optional.of(
                new PendingCommit(
                    oldSha256,
                    candidateSha256,
                    candidate,
                    temporaryJson,
                    newlyPlacedAssets,
                    stagedAssets,
                    recoveryFile));
        temporaryJson = null;
        return commitResult;
      }
      temporaryJson = null;
      List<String> warnings = cleanupAfterCommit(projectRoot, session, stagedAssets, recoveryFile);
      return SaveResult.committed(warnings);
    } catch (StorageException exception) {
      cleanupBeforeCommit(temporaryJson, newlyPlacedAssets);
      return SaveResult.notCommitted(exception.code());
    } catch (EditingSessionException exception) {
      cleanupBeforeCommit(temporaryJson, newlyPlacedAssets);
      throw exception;
    } catch (RuntimeException exception) {
      cleanupBeforeCommit(temporaryJson, newlyPlacedAssets);
      return SaveResult.notCommitted("save-validation-failed");
    } finally {
      transactionLock.unlock();
    }
  }

  public OutcomeResolution resolveUnknownOutcome(
      Path projectRoot, ProjectEditingSession session, ProjectFileLock projectLock) {
    transactionLock.lock();
    try {
      requireLock(projectLock);
      PendingCommit pending =
          pendingCommit.orElseThrow(() -> new StorageException("no-pending-commit"));
      String actual = StorageHashes.sha256(files.read(projectRoot.resolve("project.json")));
      if (actual.equals(pending.newSha256())) {
        session.resumeAfterOutcomeResolution(
            pending.candidate(), fingerprints.calculate(pending.candidate()), pending.newSha256());
        cleanupTemporaryJson(pending.temporaryJson());
        cleanupAfterCommit(projectRoot, session, pending.stagedAssets(), pending.recoveryFile());
        pendingCommit = Optional.empty();
        return OutcomeResolution.COMMITTED;
      }
      if (actual.equals(pending.oldSha256())) {
        cleanupBeforeCommit(pending.temporaryJson(), pending.newlyPlacedAssets());
        session.resumeEditing();
        pendingCommit = Optional.empty();
        return OutcomeResolution.NOT_COMMITTED;
      }
      return OutcomeResolution.UNRESOLVED;
    } catch (StorageException exception) {
      return OutcomeResolution.UNRESOLVED;
    } finally {
      transactionLock.unlock();
    }
  }

  private SaveResult commitJson(
      Path projectJson,
      Path temporaryJson,
      String oldSha256,
      String candidateSha256,
      ProjectAggregate candidate,
      ProjectEditingSession session) {
    try {
      files.atomicMoveReplacing(temporaryJson, projectJson);
    } catch (StorageException exception) {
      return resolveMoveFailure(projectJson, oldSha256, candidateSha256, candidate, session);
    }
    try {
      if (!StorageHashes.sha256(files.read(projectJson)).equals(candidateSha256)) {
        session.stopEditing();
        return SaveResult.outcomeUnknown();
      }
      session.markSaved(candidate, fingerprints.calculate(candidate), candidateSha256);
      return SaveResult.committed(List.of());
    } catch (StorageException exception) {
      session.stopEditing();
      return SaveResult.outcomeUnknown();
    }
  }

  private SaveResult resolveMoveFailure(
      Path projectJson,
      String oldSha256,
      String candidateSha256,
      ProjectAggregate candidate,
      ProjectEditingSession session) {
    try {
      String actual = StorageHashes.sha256(files.read(projectJson));
      if (actual.equals(candidateSha256)) {
        session.markSaved(candidate, fingerprints.calculate(candidate), candidateSha256);
        return SaveResult.committed(List.of());
      }
      if (actual.equals(oldSha256)) {
        return SaveResult.notCommitted("storage-atomic-move-failed");
      }
    } catch (StorageException ignored) {
      // 原子的移動の結果を読み戻せない場合は結果不明として扱う。
    }
    session.stopEditing();
    return SaveResult.outcomeUnknown();
  }

  private void placeStagedAssets(
      Path projectRoot, List<StagedAsset> stagedAssets, List<Path> newlyPlaced) {
    Path normalizedRoot = projectRoot.toAbsolutePath().normalize();
    for (StagedAsset staged : stagedAssets) {
      byte[] source = files.read(staged.source());
      if (source.length != staged.metadata().sizeBytes()
          || !StorageHashes.sha256(source).equals(staged.metadata().sha256())) {
        throw new StorageException("staged-asset-invalid");
      }
      Path target = normalizedRoot.resolve(staged.metadata().relativePath()).normalize();
      if (!target.startsWith(normalizedRoot) || files.exists(target)) {
        throw new StorageException("asset-id-collision");
      }
      Path temporary = target.resolveSibling(".asset-" + uuidSource.next().toString() + ".tmp");
      files.copyAndForce(staged.source(), temporary);
      files.atomicMoveNew(temporary, target);
      newlyPlaced.add(target);
      if (!StorageHashes.sha256(files.read(target)).equals(staged.metadata().sha256())) {
        throw new StorageException("staged-asset-copy-invalid");
      }
    }
  }

  private void requireCapacity(Path projectRoot, long jsonBytes, List<StagedAsset> stagedAssets) {
    long assetBytes = stagedAssets.stream().mapToLong(asset -> asset.metadata().sizeBytes()).sum();
    long required = Math.addExact(jsonBytes * 2L, assetBytes * 2L);
    if (files.usableSpace(projectRoot) < required) {
      throw new StorageException("storage-space-insufficient");
    }
  }

  private void requireLock(ProjectFileLock projectLock) {
    if (projectLock == null || !projectLock.valid()) {
      throw new StorageException("project-lock-lost");
    }
  }

  private ProjectAggregate withUpdatedAt(ProjectAggregate project, Instant savedAt) {
    ProjectMetadata metadata =
        new ProjectMetadata(
            project.metadata().id(),
            project.metadata().name(),
            project.metadata().createdAt(),
            savedAt);
    return new ProjectAggregate(
        metadata,
        project.background(),
        project.areas().values(),
        project.graves().values(),
        project.people().values(),
        project.assets().values());
  }

  private List<String> cleanupAfterCommit(
      Path projectRoot,
      ProjectEditingSession session,
      List<StagedAsset> stagedAssets,
      Path recoveryFile) {
    List<String> warnings = new ArrayList<>();
    for (StagedAsset staged : stagedAssets) {
      tryDelete(staged.source(), "staged-asset-cleanup-failed", warnings);
    }
    if (recoveryFile != null) {
      tryDelete(recoveryFile, "recovery-cleanup-failed", warnings);
    }
    cleanupUnreferencedAssets(projectRoot, session, warnings);
    return List.copyOf(warnings);
  }

  private void cleanupUnreferencedAssets(
      Path projectRoot, ProjectEditingSession session, List<String> warnings) {
    Set<jp.hakamap.project.domain.value.AssetId> referenced = new HashSet<>();
    session.current().assets().values().forEach(asset -> referenced.add(asset.id()));
    referenced.addAll(session.retainedAssetIds());
    for (String directory : List.of("assets/backgrounds", "assets/attachments")) {
      for (Path path : files.list(projectRoot.resolve(directory))) {
        String fileName = path.getFileName().toString();
        int extension = fileName.lastIndexOf('.');
        if (extension <= 0) {
          continue;
        }
        try {
          var assetId =
              new jp.hakamap.project.domain.value.AssetId(
                  java.util.UUID.fromString(fileName.substring(0, extension)));
          if (!referenced.contains(assetId)) {
            tryDelete(path, "asset-cleanup-failed", warnings);
          }
        } catch (IllegalArgumentException ignored) {
          // UUID形式でない不明ファイルは利用者データの可能性があるため削除しない。
        }
      }
    }
  }

  private void cleanupBeforeCommit(Path temporaryJson, List<Path> newlyPlacedAssets) {
    cleanupTemporaryJson(temporaryJson);
    cleanupPlacedAssets(newlyPlacedAssets);
  }

  private void cleanupTemporaryJson(Path temporaryJson) {
    if (temporaryJson == null) {
      return;
    }
    try {
      files.deleteIfExists(temporaryJson);
    } catch (StorageException ignored) {
      // 次回起動時の既知一時ファイル清掃へ回す。
    }
  }

  private void cleanupPlacedAssets(List<Path> paths) {
    for (Path path : paths) {
      try {
        files.deleteIfExists(path);
      } catch (StorageException ignored) {
        // 旧JSONから未参照のため、次回起動時の清掃へ回す。
      }
    }
  }

  private void tryDelete(Path path, String warning, List<String> warnings) {
    try {
      files.deleteIfExists(path);
    } catch (StorageException exception) {
      warnings.add(warning);
    }
  }

  private record PendingCommit(
      String oldSha256,
      String newSha256,
      ProjectAggregate candidate,
      Path temporaryJson,
      List<Path> newlyPlacedAssets,
      List<StagedAsset> stagedAssets,
      Path recoveryFile) {
    private PendingCommit {
      newlyPlacedAssets = List.copyOf(newlyPlacedAssets);
      stagedAssets = List.copyOf(stagedAssets);
    }
  }
}
