package jp.hakamap.project.domain.model;

import java.time.Instant;
import java.util.Objects;
import jp.hakamap.project.domain.value.ProjectId;
import jp.hakamap.project.domain.value.ProjectName;

public record ProjectMetadata(
    ProjectId id, ProjectName name, Instant createdAt, Instant updatedAt) {
  public ProjectMetadata {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(createdAt, "createdAt");
    Objects.requireNonNull(updatedAt, "updatedAt");
    if (updatedAt.isBefore(createdAt)) {
      throw new IllegalArgumentException("project-updated-before-created");
    }
  }
}
