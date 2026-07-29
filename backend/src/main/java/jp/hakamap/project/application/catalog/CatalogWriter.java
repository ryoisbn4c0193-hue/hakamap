package jp.hakamap.project.application.catalog;

import java.nio.file.Path;
import jp.hakamap.persistence.json.model.catalog.CatalogFileV1;

@FunctionalInterface
public interface CatalogWriter {
  void write(Path catalogFile, CatalogFileV1 catalog);
}
