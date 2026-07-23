package jp.hakamap.persistence.json.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jp.hakamap.persistence.json.PersistenceTestFixtures;
import jp.hakamap.persistence.json.model.project.ProjectFileV1;
import jp.hakamap.project.domain.model.Area;
import jp.hakamap.project.domain.model.Grave;
import jp.hakamap.project.domain.model.ProjectAggregate;
import jp.hakamap.project.domain.model.ProjectMetadata;
import jp.hakamap.project.domain.value.AreaColorPreset;
import jp.hakamap.project.domain.value.AreaId;
import jp.hakamap.project.domain.value.AreaName;
import jp.hakamap.project.domain.value.DisplayOrder;
import jp.hakamap.project.domain.value.GraveId;
import jp.hakamap.project.domain.value.MapRectangle;
import jp.hakamap.project.domain.value.ProjectId;
import jp.hakamap.project.domain.value.ProjectName;
import jp.hakamap.project.domain.value.RotationDegrees;
import org.junit.jupiter.api.Test;

class ProjectFileV1MapperTest {
  private static final Instant TIME = Instant.parse("2026-01-02T03:04:05.006789Z");

  private final ProjectFileV1Mapper mapper = new ProjectFileV1Mapper();

  @Test
  void normalizesOrderAndRoundTripsDomain() {
    Area later =
        new Area(
            new AreaId(UUID.fromString("22222222-2222-4222-8222-222222222222")),
            new AreaName("二組"),
            new MapRectangle(
                BigDecimal.valueOf(20), BigDecimal.ZERO, BigDecimal.TEN, BigDecimal.TEN),
            AreaColorPreset.GREEN,
            true,
            new DisplayOrder(1));
    Area first =
        new Area(
            new AreaId(UUID.fromString("33333333-3333-4333-8333-333333333333")),
            new AreaName("一組"),
            new MapRectangle(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.TEN, BigDecimal.TEN),
            AreaColorPreset.BLUE,
            true,
            new DisplayOrder(0));
    Grave grave =
        new Grave(
            new GraveId(UUID.fromString("44444444-4444-4444-8444-444444444444")),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            new MapRectangle(BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE),
            new RotationDegrees(BigDecimal.ZERO),
            TIME);
    ProjectAggregate source =
        new ProjectAggregate(
            new ProjectMetadata(
                new ProjectId(PersistenceTestFixtures.PROJECT_ID),
                new ProjectName("テスト墓地"),
                TIME,
                TIME),
            Optional.empty(),
            List.of(later, first),
            List.of(grave),
            List.of(),
            List.of());

    ProjectFileV1 file = mapper.toFile(source);
    ProjectFileV1 roundTrip = mapper.toFile(mapper.toDomain(file));

    assertThat(file.areas()).extracting(area -> area.name()).containsExactly("一組", "二組");
    assertThat(file).isEqualTo(roundTrip);
    assertThat(file.project().createdAt()).isEqualTo(Instant.parse("2026-01-02T03:04:05.006Z"));
  }
}
