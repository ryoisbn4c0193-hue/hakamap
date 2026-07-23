package jp.hakamap.project.infrastructure.storage;

import java.nio.file.Path;
import java.util.Comparator;
import jp.hakamap.persistence.json.DefensiveJsonCodec;
import jp.hakamap.persistence.json.JsonDocumentType;
import jp.hakamap.persistence.json.model.catalog.CatalogFileV1;
import jp.hakamap.persistence.json.validation.CatalogFileV1Validator;
import jp.hakamap.project.domain.service.UuidSource;

public final class CatalogStorageTransaction {
  private final StorageFileOperations files;

  private final DefensiveJsonCodec codec;

  private final CatalogFileV1Validator validator;

  private final UuidSource uuidSource;

  public CatalogStorageTransaction(
      StorageFileOperations files,
      DefensiveJsonCodec codec,
      CatalogFileV1Validator validator,
      UuidSource uuidSource) {
    this.files = files;
    this.codec = codec;
    this.validator = validator;
    this.uuidSource = uuidSource;
  }

  public synchronized SaveResult write(Path catalogFile, CatalogFileV1 catalog) {
    Path directory = catalogFile.toAbsolutePath().normalize().getParent();
    Path candidateTemporary =
        directory.resolve(".catalog-" + uuidSource.next().toString() + ".tmp");
    Path backupTemporary =
        directory.resolve(".catalog-backup-" + uuidSource.next().toString() + ".tmp");
    try {
      CatalogFileV1 ordered =
          new CatalogFileV1(
              catalog.schemaVersion(),
              catalog.defaultProjectId(),
              catalog.projects().stream()
                  .sorted(Comparator.comparing(project -> project.projectId().toString()))
                  .toList());
      byte[] candidate = codec.write(ordered, JsonDocumentType.CATALOG);
      validator.validate(ordered);
      if (files.exists(catalogFile)) {
        byte[] current = files.read(catalogFile);
        CatalogFileV1 currentCatalog =
            codec.read(current, JsonDocumentType.CATALOG, CatalogFileV1.class);
        validator.validate(currentCatalog);
        files.writeAndForce(backupTemporary, current);
        CatalogFileV1 backupCandidate =
            codec.read(files.read(backupTemporary), JsonDocumentType.CATALOG, CatalogFileV1.class);
        validator.validate(backupCandidate);
        files.atomicMoveReplacing(backupTemporary, catalogFile.resolveSibling("catalog.json.bak"));
      }
      files.writeAndForce(candidateTemporary, candidate);
      CatalogFileV1 reread =
          codec.read(files.read(candidateTemporary), JsonDocumentType.CATALOG, CatalogFileV1.class);
      validator.validate(reread);
      files.atomicMoveReplacing(candidateTemporary, catalogFile);
      CatalogFileV1 committed =
          codec.read(files.read(catalogFile), JsonDocumentType.CATALOG, CatalogFileV1.class);
      validator.validate(committed);
      return SaveResult.committed(java.util.List.of());
    } catch (RuntimeException exception) {
      cleanup(candidateTemporary);
      cleanup(backupTemporary);
      return SaveResult.notCommitted("catalog-save-failed");
    }
  }

  private void cleanup(Path path) {
    try {
      files.deleteIfExists(path);
    } catch (StorageException ignored) {
      // 次回起動時の既知一時ファイル清掃へ回す。
    }
  }
}
