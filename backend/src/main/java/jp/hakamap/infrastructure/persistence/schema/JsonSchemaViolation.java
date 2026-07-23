package jp.hakamap.infrastructure.persistence.schema;

/** 入力値を含まないJSON Schema違反情報です。 */
public record JsonSchemaViolation(String keyword, String instanceLocation) {}
