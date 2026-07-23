package jp.hakamap.project.application.command;

import jp.hakamap.project.application.history.CommandType;
import jp.hakamap.project.domain.model.ProjectAggregate;

public interface ProjectCommand<R> {
  CommandType type();

  CommandEvaluation<R> evaluate(ProjectAggregate current, CommandContext context);
}
