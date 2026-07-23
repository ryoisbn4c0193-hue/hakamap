package jp.hakamap.project.application.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import jp.hakamap.persistence.json.PersistenceTestFixtures;
import jp.hakamap.project.application.history.CommandId;
import jp.hakamap.project.application.history.CommandType;
import jp.hakamap.project.application.history.EditingSessionException;
import jp.hakamap.project.application.history.ProjectChangeSet;
import jp.hakamap.project.application.history.ProjectEditingSession;
import jp.hakamap.project.application.history.ProjectFingerprintCalculator;
import jp.hakamap.project.application.history.ValueDelta;
import jp.hakamap.project.domain.model.Area;
import jp.hakamap.project.domain.model.ProjectAggregate;
import jp.hakamap.project.domain.value.AreaColorPreset;
import jp.hakamap.project.domain.value.AreaId;
import jp.hakamap.project.domain.value.AreaName;
import jp.hakamap.project.domain.value.DisplayOrder;
import jp.hakamap.project.domain.value.MapRectangle;
import jp.hakamap.project.domain.value.ProjectName;
import org.junit.jupiter.api.Test;

class ProjectCommandExecutorTest {
  private static final Instant COMMAND_TIME = Instant.parse("2026-03-04T05:06:07.008Z");

  private final ProjectFingerprintCalculator fingerprints =
      new ProjectFingerprintCalculator(
          PersistenceTestFixtures.codec(),
          new jp.hakamap.persistence.json.mapper.ProjectFileV1Mapper());

  private final ProjectCommandExecutor executor =
      new ProjectCommandExecutor(
          Clock.fixed(COMMAND_TIME, ZoneOffset.UTC),
          () -> UUID.fromString("77777777-7777-4777-8777-777777777777"));

  @Test
  void rejectsStaleRevisionBeforeEvaluatingCommand() {
    ProjectEditingSession session = session();
    AtomicBoolean evaluated = new AtomicBoolean();

    assertThatThrownBy(
            () ->
                executor.execute(
                    session,
                    1,
                    command(
                        current -> {
                          evaluated.set(true);
                          return renameChangeSet();
                        })))
        .isInstanceOfSatisfying(
            EditingSessionException.class,
            exception -> assertThat(exception.getMessage()).isEqualTo("project-revision-conflict"));

    assertThat(evaluated).isFalse();
    assertThat(session.current().metadata().name()).isEqualTo(new ProjectName("テスト墓地"));
    assertThat(session.revision()).isZero();
  }

  @Test
  void evaluatesCommandAgainstDetachedAggregate() {
    ProjectEditingSession session = session();
    Area evaluationOnlyArea =
        new Area(
            new AreaId(UUID.fromString("88888888-8888-4888-8888-888888888888")),
            new AreaName("評価用エリア"),
            new MapRectangle(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.TEN, BigDecimal.TEN),
            AreaColorPreset.BLUE,
            true,
            new DisplayOrder(0));

    executor.execute(
        session,
        0,
        command(
            current -> {
              current.addArea(evaluationOnlyArea);
              return renameChangeSet();
            }));

    assertThat(session.current().areas()).isEmpty();
    assertThat(session.current().metadata().name()).isEqualTo(new ProjectName("変更後墓地"));
    assertThat(session.revision()).isEqualTo(1);
    assertThat(session.undoSize()).isEqualTo(1);
  }

  private ProjectEditingSession session() {
    return new ProjectEditingSession(
        PersistenceTestFixtures.emptyProject(), "0".repeat(64), fingerprints);
  }

  private ProjectCommand<Void> command(
      java.util.function.Function<ProjectAggregate, ProjectChangeSet> evaluator) {
    return new ProjectCommand<>() {
      @Override
      public CommandType type() {
        return CommandType.RENAME_PROJECT;
      }

      @Override
      public CommandEvaluation<Void> evaluate(ProjectAggregate current, CommandContext context) {
        return new CommandEvaluation<>(null, evaluator.apply(current));
      }
    };
  }

  private ProjectChangeSet renameChangeSet() {
    return new ProjectChangeSet(
        new CommandId(UUID.fromString("99999999-9999-4999-8999-999999999999")),
        CommandType.RENAME_PROJECT,
        COMMAND_TIME,
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        Optional.of(ValueDelta.changed(new ProjectName("テスト墓地"), new ProjectName("変更後墓地"))),
        Optional.empty(),
        Set.of());
  }
}
