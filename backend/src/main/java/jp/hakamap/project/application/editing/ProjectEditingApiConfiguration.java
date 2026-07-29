package jp.hakamap.project.application.editing;

import java.time.Clock;
import jp.hakamap.infrastructure.fileselection.FileSelectionService;
import jp.hakamap.project.application.catalog.CatalogPaths;
import jp.hakamap.project.application.catalog.OpenProjectManager;
import jp.hakamap.project.application.history.ProjectFingerprintCalculator;
import jp.hakamap.project.domain.service.UuidSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ProjectEditingApiConfiguration {
  @Bean
  ProjectAssetStaging projectAssetStaging(CatalogPaths paths) {
    return new ProjectAssetStaging(paths.temporaryAssetRoot());
  }

  @Bean
  ProjectEditingApiService projectEditingApiService(
      OpenProjectManager openProjects,
      ProjectFingerprintCalculator fingerprints,
      Clock clock,
      UuidSource uuids,
      FileSelectionService fileSelections,
      ProjectAssetStaging assetStaging) {
    return new ProjectEditingApiService(
        openProjects, fingerprints, clock, uuids, fileSelections, assetStaging);
  }
}
