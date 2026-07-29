package jp.hakamap.project.application.catalog;

import java.time.Clock;
import java.util.UUID;
import jp.hakamap.infrastructure.fileselection.FileSelectionService;
import jp.hakamap.infrastructure.persistence.schema.ClasspathJsonSchemaValidator;
import jp.hakamap.persistence.json.DefensiveJsonCodec;
import jp.hakamap.persistence.json.mapper.ProjectFileV1Mapper;
import jp.hakamap.persistence.json.repository.CatalogRepository;
import jp.hakamap.persistence.json.repository.FileCatalogRepository;
import jp.hakamap.persistence.json.repository.FileProjectRepository;
import jp.hakamap.persistence.json.repository.ProjectRepository;
import jp.hakamap.persistence.json.validation.CatalogFileV1Validator;
import jp.hakamap.persistence.json.validation.ProjectAssetFileValidator;
import jp.hakamap.project.application.history.ProjectFingerprintCalculator;
import jp.hakamap.project.domain.service.UuidSource;
import jp.hakamap.project.infrastructure.storage.CatalogStorageTransaction;
import jp.hakamap.project.infrastructure.storage.CommitStatus;
import jp.hakamap.project.infrastructure.storage.NioStorageFileOperations;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ProjectCatalogConfiguration {
  @Bean
  CatalogPaths catalogPaths() {
    return CatalogPaths.forCurrentUser(System.getenv());
  }

  @Bean
  CatalogRepository catalogRepository(ClasspathJsonSchemaValidator schemas) {
    return new FileCatalogRepository(new DefensiveJsonCodec(schemas), new CatalogFileV1Validator());
  }

  @Bean
  ProjectRepository projectRepository(ClasspathJsonSchemaValidator schemas) {
    return new FileProjectRepository(
        new DefensiveJsonCodec(schemas),
        new ProjectFileV1Mapper(),
        new ProjectAssetFileValidator());
  }

  @Bean
  ProjectFingerprintCalculator projectFingerprintCalculator(ClasspathJsonSchemaValidator schemas) {
    return new ProjectFingerprintCalculator(
        new DefensiveJsonCodec(schemas), new ProjectFileV1Mapper());
  }

  @Bean
  CatalogWriter catalogWriter(ClasspathJsonSchemaValidator schemas, UuidSource uuids) {
    CatalogStorageTransaction transaction =
        new CatalogStorageTransaction(
            new NioStorageFileOperations(),
            new DefensiveJsonCodec(schemas),
            new CatalogFileV1Validator(),
            uuids);
    return (path, catalog) -> {
      if (transaction.write(path, catalog).status() != CommitStatus.COMMITTED) {
        throw new ProjectCatalogException("catalog-save-failed");
      }
    };
  }

  @Bean
  UuidSource uuidSource() {
    return UUID::randomUUID;
  }

  @Bean(destroyMethod = "close")
  OpenProjectManager openProjectManager() {
    return new OpenProjectManager();
  }

  @Bean
  ProjectCatalogService projectCatalogService(
      CatalogPaths paths,
      CatalogRepository catalogs,
      CatalogWriter catalogWriter,
      ProjectRepository projects,
      FileSelectionService selections,
      OpenProjectManager openProjects,
      Clock clock,
      UuidSource uuids) {
    return new ProjectCatalogService(
        paths, catalogs, catalogWriter, projects, selections, openProjects, clock, uuids);
  }
}
