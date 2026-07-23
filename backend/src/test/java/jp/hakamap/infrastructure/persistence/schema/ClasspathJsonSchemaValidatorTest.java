package jp.hakamap.infrastructure.persistence.schema;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ClasspathJsonSchemaValidatorTest {

  private final ClasspathJsonSchemaValidator validator = new ClasspathJsonSchemaValidator();

  @Test
  void loadsEveryBundledSchema() {
    assertThat(JsonSchemaType.values())
        .allSatisfy(
            type ->
                assertThat(validator.validate(type, minimalDocument(type)))
                    .as(type + "の最小文書")
                    .isEmpty());
  }

  @Test
  void rejectsUnknownProperty() {
    String json =
        """
        {
          "schemaVersion": 1,
          "projects": [],
          "unknown": true
        }
        """;

    assertThat(validator.validate(JsonSchemaType.CATALOG, json))
        .extracting(JsonSchemaViolation::keyword)
        .contains("additionalProperties");
  }

  private static String minimalDocument(JsonSchemaType type) {
    return switch (type) {
      case PROJECT -> minimalProject();
      case CATALOG ->
          """
          {
            "schemaVersion": 1,
            "projects": []
          }
          """;
      case RECOVERY ->
          """
          {
            "recoverySchemaVersion": 1,
            "applicationVersion": "0.0.1",
            "projectId": "00000000-0000-4000-8000-000000000001",
            "createdAt": "2026-07-23T00:00:00.000Z",
            "baseProjectSha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            "projectSnapshot": %s,
            "stagedAssets": []
          }
          """
              .formatted(minimalProject());
    };
  }

  private static String minimalProject() {
    return """
        {
          "schemaVersion": 1,
          "project": {
            "id": "00000000-0000-4000-8000-000000000001",
            "name": "テスト",
            "createdAt": "2026-07-23T00:00:00.000Z",
            "updatedAt": "2026-07-23T00:00:00.000Z"
          },
          "areas": [],
          "graves": [],
          "people": [],
          "assets": []
        }
        """;
  }
}
