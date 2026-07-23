package jp.hakamap.persistence.json.repository;

import java.nio.file.Path;
import jp.hakamap.persistence.json.DefensiveJsonCodec;
import jp.hakamap.persistence.json.JsonDocumentType;
import jp.hakamap.persistence.json.mapper.ProjectFileV1Mapper;
import jp.hakamap.persistence.json.model.project.ProjectFileV1;
import jp.hakamap.persistence.json.validation.ProjectAssetFileValidator;
import jp.hakamap.project.domain.model.ProjectAggregate;

public final class FileProjectRepository implements ProjectRepository {
  private final DefensiveJsonCodec codec;

  private final ProjectFileV1Mapper mapper;

  private final ProjectAssetFileValidator assetValidator;

  public FileProjectRepository(
      DefensiveJsonCodec codec,
      ProjectFileV1Mapper mapper,
      ProjectAssetFileValidator assetValidator) {
    this.codec = codec;
    this.mapper = mapper;
    this.assetValidator = assetValidator;
  }

  @Override
  public ProjectAggregate read(Path projectRoot) {
    ProjectFileV1 file =
        codec.read(
            RepositoryFiles.read(projectRoot.resolve("project.json")),
            JsonDocumentType.PROJECT,
            ProjectFileV1.class);
    ProjectAggregate project = mapper.toDomain(file);
    assetValidator.validate(projectRoot, project);
    return project;
  }

  @Override
  public void write(Path projectRoot, ProjectAggregate project) {
    byte[] bytes = codec.write(mapper.toFile(project), JsonDocumentType.PROJECT);
    assetValidator.validate(projectRoot, project);
    RepositoryFiles.write(projectRoot.resolve("project.json"), bytes);
  }
}
