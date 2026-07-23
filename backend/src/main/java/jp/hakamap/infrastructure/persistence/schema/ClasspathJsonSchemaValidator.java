package jp.hakamap.infrastructure.persistence.schema;

import com.networknt.schema.Error;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SchemaRegistryConfig;
import com.networknt.schema.SpecificationVersion;
import java.io.IOException;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/** classpathに同梱したJSON Schemaだけを使用する構造検証サービスです。 */
@Component
public final class ClasspathJsonSchemaValidator {

  private final Map<JsonSchemaType, Schema> schemas;

  public ClasspathJsonSchemaValidator() {
    SchemaRegistryConfig config =
        SchemaRegistryConfig.builder()
            .failFast(false)
            .formatAssertionsEnabled(false)
            .locale(Locale.ROOT)
            .typeLoose(false)
            .build();
    SchemaRegistry registry =
        SchemaRegistry.withDefaultDialect(
            SpecificationVersion.DRAFT_2020_12,
            builder -> builder.schemaRegistryConfig(config).schemaCacheEnabled(true));

    EnumMap<JsonSchemaType, Schema> loadedSchemas = new EnumMap<>(JsonSchemaType.class);
    for (JsonSchemaType type : JsonSchemaType.values()) {
      loadedSchemas.put(type, loadSchema(registry, type));
    }
    this.schemas = Map.copyOf(loadedSchemas);
  }

  /**
   * JSON文字列を指定したSchemaで検証します。
   *
   * @param type Schema種別
   * @param json 検証対象のJSON
   * @return 入力値を含まない違反一覧
   */
  public List<JsonSchemaViolation> validate(JsonSchemaType type, String json) {
    return schemas.get(type).validate(json, InputFormat.JSON).stream()
        .map(ClasspathJsonSchemaValidator::toViolation)
        .toList();
  }

  private static Schema loadSchema(SchemaRegistry registry, JsonSchemaType type) {
    ClassPathResource resource = new ClassPathResource(type.classpathLocation());
    try (InputStream input = resource.getInputStream()) {
      return registry.getSchema(input, InputFormat.JSON);
    } catch (IOException | RuntimeException exception) {
      throw new IllegalStateException("同梱JSON Schemaを読み込めません。種別: " + type, exception);
    }
  }

  private static JsonSchemaViolation toViolation(Error error) {
    return new JsonSchemaViolation(error.getKeyword(), error.getInstanceLocation().toString());
  }
}
