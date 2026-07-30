package jp.hakamap.project.application.transfer;

import java.time.Clock;
import jp.hakamap.infrastructure.fileselection.FileSelectionService;
import jp.hakamap.persistence.json.repository.ProjectRepository;
import jp.hakamap.project.application.catalog.OpenProjectManager;
import jp.hakamap.project.application.catalog.ProjectCatalogService;
import jp.hakamap.project.application.history.ProjectFingerprintCalculator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ProjectTransferConfiguration {
  @Bean
  ProjectArchiveService projectArchiveService(
      ProjectRepository projects,
      Clock clock,
      @Value("${hakamap.application-version:development}") String applicationVersion) {
    return new ProjectArchiveService(projects, clock, applicationVersion);
  }

  @Bean
  ProjectTransferService projectTransferService(
      ProjectArchiveService archives,
      OpenProjectManager openProjects,
      ProjectRepository projects,
      ProjectFingerprintCalculator fingerprints,
      FileSelectionService selections,
      ProjectCatalogService catalog,
      Clock clock) {
    return new ProjectTransferService(
        archives, openProjects, projects, fingerprints, selections, catalog, clock);
  }

  @Bean(destroyMethod = "close")
  OperationRegistry operationRegistry(Clock clock) {
    return new OperationRegistry(clock);
  }
}
