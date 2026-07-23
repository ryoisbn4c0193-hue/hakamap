package jp.hakamap.persistence.json;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jp.hakamap.persistence.json.mapper.ProjectFileV1Mapper;
import jp.hakamap.persistence.json.model.project.ProjectFileV1;
import jp.hakamap.project.domain.model.ProjectAggregate;
import jp.hakamap.project.domain.model.ProjectMetadata;
import jp.hakamap.project.domain.value.ProjectId;
import jp.hakamap.project.domain.value.ProjectName;

public final class PersistenceTestFixtures {
  public static final UUID PROJECT_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");

  public static final Instant CREATED_AT = Instant.parse("2026-01-02T03:04:05.006Z");

  private PersistenceTestFixtures() {}

  public static DefensiveJsonCodec codec() {
    return new DefensiveJsonCodec(
        new jp.hakamap.infrastructure.persistence.schema.ClasspathJsonSchemaValidator());
  }

  public static ProjectAggregate emptyProject() {
    return new ProjectAggregate(
        new ProjectMetadata(
            new ProjectId(PROJECT_ID), new ProjectName("テスト墓地"), CREATED_AT, CREATED_AT),
        Optional.empty(),
        List.of(),
        List.of(),
        List.of(),
        List.of());
  }

  public static ProjectFileV1 emptyProjectFile() {
    return new ProjectFileV1Mapper().toFile(emptyProject());
  }
}
