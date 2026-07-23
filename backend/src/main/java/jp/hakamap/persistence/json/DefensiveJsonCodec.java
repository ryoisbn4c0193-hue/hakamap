package jp.hakamap.persistence.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.List;
import jp.hakamap.infrastructure.persistence.schema.ClasspathJsonSchemaValidator;
import jp.hakamap.infrastructure.persistence.schema.JsonSchemaViolation;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

public final class DefensiveJsonCodec {
  private static final int MAXIMUM_NESTING_DEPTH = 50;

  private static final int MAXIMUM_STRING_LENGTH = 1024 * 1024;

  private static final int MAXIMUM_NUMBER_LENGTH = 64;

  private final ObjectMapper mapper;

  private final ClasspathJsonSchemaValidator schemaValidator;

  public DefensiveJsonCodec(ClasspathJsonSchemaValidator schemaValidator) {
    this.schemaValidator = schemaValidator;
    StreamReadConstraints constraints =
        StreamReadConstraints.builder()
            .maxDocumentLength(JsonDocumentType.RECOVERY.maximumBytes())
            .maxNestingDepth(MAXIMUM_NESTING_DEPTH)
            .maxStringLength(MAXIMUM_STRING_LENGTH)
            .maxNumberLength(MAXIMUM_NUMBER_LENGTH)
            .build();
    JsonFactory factory = JsonFactory.builder().streamReadConstraints(constraints).build();
    this.mapper =
        JsonMapper.builder(factory)
            .findAndAddModules()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .enable(SerializationFeature.INDENT_OUTPUT)
            .changeDefaultPropertyInclusion(
                ignored ->
                    JsonInclude.Value.construct(
                        JsonInclude.Include.NON_NULL, JsonInclude.Include.ALWAYS))
            .build();
  }

  public <T> T read(byte[] bytes, JsonDocumentType type, Class<T> modelType) {
    requireSize(bytes, type);
    String json = decodeUtf8(bytes);
    JsonNode root = parseTree(json);
    requireSupportedVersion(root, type);
    validateSchema(type, json);
    if (type == JsonDocumentType.RECOVERY) {
      validateRecoveryProjectSchema(root);
    }
    try {
      return mapper.treeToValue(root, modelType);
    } catch (RuntimeException exception) {
      throw new JsonPersistenceException("json-model-invalid", exception);
    }
  }

  public byte[] write(Object value, JsonDocumentType type) {
    try {
      byte[] bytes = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(value);
      requireSize(bytes, type);
      read(bytes, type, value.getClass());
      return bytes;
    } catch (JsonPersistenceException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      throw new JsonPersistenceException("json-write-failed", exception);
    }
  }

  private JsonNode parseTree(String json) {
    try {
      return mapper.readTree(json);
    } catch (RuntimeException exception) {
      throw new JsonPersistenceException("json-syntax-invalid", exception);
    }
  }

  private void validateRecoveryProjectSchema(JsonNode recoveryRoot) {
    JsonNode projectSnapshot = recoveryRoot.get("projectSnapshot");
    if (projectSnapshot == null) {
      throw new JsonPersistenceException("json-schema-invalid");
    }
    validateSchema(JsonDocumentType.PROJECT, projectSnapshot.toString());
  }

  private void validateSchema(JsonDocumentType type, String json) {
    List<JsonSchemaViolation> violations = schemaValidator.validate(type.schemaType(), json);
    if (!violations.isEmpty()) {
      throw new JsonPersistenceException("json-schema-invalid");
    }
  }

  private void requireSupportedVersion(JsonNode root, JsonDocumentType type) {
    JsonNode version = root.get(type.versionProperty());
    if (version == null || !version.isInt() || version.intValue() != 1) {
      throw new JsonPersistenceException("json-version-unsupported");
    }
  }

  private void requireSize(byte[] bytes, JsonDocumentType type) {
    if (bytes == null || bytes.length > type.maximumBytes()) {
      throw new JsonPersistenceException("json-size-exceeded");
    }
  }

  private String decodeUtf8(byte[] bytes) {
    try {
      return StandardCharsets.UTF_8
          .newDecoder()
          .onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT)
          .decode(ByteBuffer.wrap(bytes))
          .toString();
    } catch (CharacterCodingException exception) {
      throw new JsonPersistenceException("json-encoding-invalid", exception);
    }
  }
}
