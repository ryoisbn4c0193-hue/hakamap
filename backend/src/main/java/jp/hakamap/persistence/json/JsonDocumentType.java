package jp.hakamap.persistence.json;

import jp.hakamap.infrastructure.persistence.schema.JsonSchemaType;

public enum JsonDocumentType {
  PROJECT(100L * 1024 * 1024, "schemaVersion", JsonSchemaType.PROJECT),
  CATALOG(10L * 1024 * 1024, "schemaVersion", JsonSchemaType.CATALOG),
  RECOVERY(110L * 1024 * 1024, "recoverySchemaVersion", JsonSchemaType.RECOVERY);

  private final long maximumBytes;

  private final String versionProperty;

  private final JsonSchemaType schemaType;

  JsonDocumentType(long maximumBytes, String versionProperty, JsonSchemaType schemaType) {
    this.maximumBytes = maximumBytes;
    this.versionProperty = versionProperty;
    this.schemaType = schemaType;
  }

  public long maximumBytes() {
    return maximumBytes;
  }

  public String versionProperty() {
    return versionProperty;
  }

  public JsonSchemaType schemaType() {
    return schemaType;
  }
}
