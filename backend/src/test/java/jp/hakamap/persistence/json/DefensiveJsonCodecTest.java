package jp.hakamap.persistence.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.List;
import jp.hakamap.persistence.json.model.project.ProjectFileV1;
import jp.hakamap.persistence.json.model.recovery.RecoveryFileV1;
import org.junit.jupiter.api.Test;

class DefensiveJsonCodecTest {
  private final DefensiveJsonCodec codec = PersistenceTestFixtures.codec();

  @Test
  void roundTripsProjectAsDeterministicUtf8Json() {
    ProjectFileV1 source = PersistenceTestFixtures.emptyProjectFile();

    byte[] first = codec.write(source, JsonDocumentType.PROJECT);
    byte[] second = codec.write(source, JsonDocumentType.PROJECT);
    ProjectFileV1 restored = codec.read(first, JsonDocumentType.PROJECT, ProjectFileV1.class);

    assertThat(first).isEqualTo(second);
    assertThat(first).hasSizeGreaterThan(3);
    assertThat(first[0]).isNotEqualTo((byte) 0xef);
    assertThat(new String(first, StandardCharsets.UTF_8))
        .contains("\"createdAt\" : \"2026-01-02T03:04:05.006Z\"");
    assertThat(restored).isEqualTo(source);
  }

  @Test
  void rejectsUnknownProperty() {
    String json =
        """
        {
          "schemaVersion": 1,
          "project": {
            "id": "11111111-1111-4111-8111-111111111111",
            "name": "テスト墓地",
            "createdAt": "2026-01-02T03:04:05.006Z",
            "updatedAt": "2026-01-02T03:04:05.006Z",
            "unknown": true
          },
          "areas": [],
          "graves": [],
          "people": [],
          "assets": []
        }
        """;

    assertThatThrownBy(
            () ->
                codec.read(
                    json.getBytes(StandardCharsets.UTF_8),
                    JsonDocumentType.PROJECT,
                    ProjectFileV1.class))
        .isInstanceOfSatisfying(
            JsonPersistenceException.class,
            exception -> assertThat(exception.code()).isEqualTo("json-schema-invalid"));
  }

  @Test
  void rejectsDuplicateKeyMalformedUtf8AndUnsupportedVersion() {
    assertCode("{\"schemaVersion\":1,\"schemaVersion\":1}", "json-syntax-invalid");
    assertThatThrownBy(
            () ->
                codec.read(
                    new byte[] {(byte) 0xc3, (byte) 0x28},
                    JsonDocumentType.PROJECT,
                    ProjectFileV1.class))
        .isInstanceOfSatisfying(
            JsonPersistenceException.class,
            exception -> assertThat(exception.code()).isEqualTo("json-encoding-invalid"));
    assertCode("{\"schemaVersion\":2}", "json-version-unsupported");
  }

  @Test
  void rejectsExcessiveNestingDepth() {
    String json = "{\"schemaVersion\":1,\"value\":" + "[".repeat(51) + "0" + "]".repeat(51) + "}";

    assertCode(json, "json-syntax-invalid");
  }

  @Test
  void appliesProjectSchemaToRecoverySnapshot() {
    RecoveryFileV1 recovery =
        new RecoveryFileV1(
            1,
            "0.0.1",
            PersistenceTestFixtures.PROJECT_ID,
            PersistenceTestFixtures.CREATED_AT,
            "0".repeat(64),
            PersistenceTestFixtures.emptyProjectFile(),
            List.of());
    String invalid =
        new String(codec.write(recovery, JsonDocumentType.RECOVERY), StandardCharsets.UTF_8)
            .replace("\"name\" : \"テスト墓地\"", "\"name\" : \"\"");

    assertThatThrownBy(
            () ->
                codec.read(
                    invalid.getBytes(StandardCharsets.UTF_8),
                    JsonDocumentType.RECOVERY,
                    RecoveryFileV1.class))
        .isInstanceOfSatisfying(
            JsonPersistenceException.class,
            exception -> assertThat(exception.code()).isEqualTo("json-schema-invalid"));
  }

  private void assertCode(String json, String code) {
    assertThatThrownBy(
            () ->
                codec.read(
                    json.getBytes(StandardCharsets.UTF_8),
                    JsonDocumentType.PROJECT,
                    ProjectFileV1.class))
        .isInstanceOfSatisfying(
            JsonPersistenceException.class,
            exception -> assertThat(exception.code()).isEqualTo(code));
  }
}
