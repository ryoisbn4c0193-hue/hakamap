package jp.hakamap.project.application.command;

import java.time.Clock;
import java.util.Objects;
import jp.hakamap.project.application.history.ProjectEditingSession;
import jp.hakamap.project.domain.service.UuidSource;

public final class ProjectCommandExecutor {
  private final Clock clock;

  private final UuidSource uuidSource;

  public ProjectCommandExecutor(Clock clock, UuidSource uuidSource) {
    this.clock = Objects.requireNonNull(clock, "clock");
    this.uuidSource = Objects.requireNonNull(uuidSource, "uuidSource");
  }

  public <R> R execute(
      ProjectEditingSession session, long expectedRevision, ProjectCommand<R> command) {
    CommandContext context = new CommandContext(expectedRevision, clock.instant(), uuidSource);
    CommandEvaluation<R> evaluation = command.evaluate(session.current(), context);
    session.apply(expectedRevision, evaluation.changeSet());
    return evaluation.result();
  }
}
