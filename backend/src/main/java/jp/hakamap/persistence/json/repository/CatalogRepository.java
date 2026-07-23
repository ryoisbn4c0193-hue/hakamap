package jp.hakamap.persistence.json.repository;

import java.nio.file.Path;
import jp.hakamap.persistence.json.model.catalog.CatalogFileV1;

public interface CatalogRepository {
  CatalogFileV1 read(Path catalogFile);

  void write(Path catalogFile, CatalogFileV1 catalog);
}
