package jp.hakamap.persistence.json.repository;

import java.nio.file.Path;
import jp.hakamap.project.domain.model.ProjectAggregate;

public interface ProjectRepository {
  ProjectAggregate read(Path projectRoot);

  void write(Path projectRoot, ProjectAggregate project);
}
