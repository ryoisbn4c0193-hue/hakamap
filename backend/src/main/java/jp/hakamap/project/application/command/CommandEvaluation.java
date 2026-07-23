package jp.hakamap.project.application.command;

import java.util.Objects;
import jp.hakamap.project.application.history.ProjectChangeSet;

public record CommandEvaluation<R>(R result, ProjectChangeSet changeSet) {
  public CommandEvaluation {
    Objects.requireNonNull(changeSet, "changeSet");
  }
}
