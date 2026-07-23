package jp.hakamap.persistence.json.repository;

import java.nio.file.Path;
import java.util.Comparator;
import jp.hakamap.persistence.json.DefensiveJsonCodec;
import jp.hakamap.persistence.json.JsonDocumentType;
import jp.hakamap.persistence.json.model.catalog.CatalogFileV1;
import jp.hakamap.persistence.json.validation.CatalogFileV1Validator;

public final class FileCatalogRepository implements CatalogRepository {
  private final DefensiveJsonCodec codec;

  private final CatalogFileV1Validator validator;

  public FileCatalogRepository(DefensiveJsonCodec codec, CatalogFileV1Validator validator) {
    this.codec = codec;
    this.validator = validator;
  }

  @Override
  public CatalogFileV1 read(Path catalogFile) {
    CatalogFileV1 catalog =
        codec.read(
            RepositoryFiles.read(catalogFile), JsonDocumentType.CATALOG, CatalogFileV1.class);
    validator.validate(catalog);
    return catalog;
  }

  @Override
  public void write(Path catalogFile, CatalogFileV1 catalog) {
    CatalogFileV1 ordered =
        new CatalogFileV1(
            catalog.schemaVersion(),
            catalog.defaultProjectId(),
            catalog.projects().stream()
                .sorted(Comparator.comparing(project -> project.projectId().toString()))
                .toList());
    byte[] bytes = codec.write(ordered, JsonDocumentType.CATALOG);
    validator.validate(ordered);
    RepositoryFiles.write(catalogFile, bytes);
  }
}
