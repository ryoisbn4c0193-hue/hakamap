package jp.hakamap.infrastructure.persistence.schema;

/** Hakamapが管理するJSON文書のSchema種別です。 */
public enum JsonSchemaType {
  PROJECT("json-schema/project/project-v1.schema.json"),
  CATALOG("json-schema/catalog/catalog-v1.schema.json"),
  RECOVERY("json-schema/recovery/recovery-v1.schema.json");

  private final String classpathLocation;

  JsonSchemaType(String classpathLocation) {
    this.classpathLocation = classpathLocation;
  }

  String classpathLocation() {
    return classpathLocation;
  }
}
