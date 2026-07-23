package jp.hakamap.persistence.json.repository;

import java.nio.file.Path;
import java.util.Comparator;
import jp.hakamap.persistence.json.DefensiveJsonCodec;
import jp.hakamap.persistence.json.JsonDocumentType;
import jp.hakamap.persistence.json.model.recovery.RecoveryFileV1;
import jp.hakamap.persistence.json.validation.RecoveryFileV1Validator;

public final class FileRecoveryRepository implements RecoveryRepository {
  private final Path temporaryAssetRoot;

  private final DefensiveJsonCodec codec;

  private final RecoveryFileV1Validator validator;

  public FileRecoveryRepository(
      Path temporaryAssetRoot, DefensiveJsonCodec codec, RecoveryFileV1Validator validator) {
    this.temporaryAssetRoot = temporaryAssetRoot;
    this.codec = codec;
    this.validator = validator;
  }

  @Override
  public RecoveryFileV1 read(Path recoveryFile) {
    RecoveryFileV1 recovery =
        codec.read(
            RepositoryFiles.read(recoveryFile), JsonDocumentType.RECOVERY, RecoveryFileV1.class);
    validator.validate(recovery, temporaryAssetRoot);
    return recovery;
  }

  @Override
  public void write(Path recoveryFile, RecoveryFileV1 recovery) {
    RecoveryFileV1 ordered =
        new RecoveryFileV1(
            recovery.recoverySchemaVersion(),
            recovery.applicationVersion(),
            recovery.projectId(),
            recovery.createdAt(),
            recovery.baseProjectSha256(),
            recovery.projectSnapshot(),
            recovery.stagedAssets().stream()
                .sorted(Comparator.comparing(staged -> staged.assetId().toString()))
                .toList());
    byte[] bytes = codec.write(ordered, JsonDocumentType.RECOVERY);
    validator.validate(ordered, temporaryAssetRoot);
    RepositoryFiles.write(recoveryFile, bytes);
  }
}
