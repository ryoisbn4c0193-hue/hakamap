package jp.hakamap.project.domain.model;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import jp.hakamap.project.domain.value.ProjectId;
import jp.hakamap.project.domain.value.ProjectName;

public record ProjectMetadata(
    ProjectId id, ProjectName name, Instant createdAt, Instant updatedAt) {
  public ProjectMetadata {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(name, "name");
    createdAt = Objects.requireNonNull(createdAt, "createdAt").truncatedTo(ChronoUnit.MILLIS);
    updatedAt = Objects.requireNonNull(updatedAt, "updatedAt").truncatedTo(ChronoUnit.MILLIS);
    if (updatedAt.isBefore(createdAt)) {
      throw new IllegalArgumentException("project-updated-before-created");
    }
  }
}
