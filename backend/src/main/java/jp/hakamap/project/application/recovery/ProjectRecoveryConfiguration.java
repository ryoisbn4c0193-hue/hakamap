package jp.hakamap.project.application.recovery;

import java.time.Clock;
import jp.hakamap.infrastructure.persistence.schema.ClasspathJsonSchemaValidator;
import jp.hakamap.persistence.json.DefensiveJsonCodec;
import jp.hakamap.persistence.json.mapper.ProjectFileV1Mapper;
import jp.hakamap.persistence.json.validation.RecoveryFileV1Validator;
import jp.hakamap.project.application.catalog.CatalogPaths;
import jp.hakamap.project.application.catalog.OpenProjectManager;
import jp.hakamap.project.application.editing.ProjectAssetStaging;
import jp.hakamap.project.application.history.ProjectFingerprintCalculator;
import jp.hakamap.project.domain.service.UuidSource;
import jp.hakamap.project.infrastructure.recovery.RecoverySnapshotService;
import jp.hakamap.project.infrastructure.storage.NioStorageFileOperations;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling
public class ProjectRecoveryConfiguration {
  @Bean
  RecoverySnapshotService recoverySnapshotService(
      ClasspathJsonSchemaValidator schemas,
      ProjectFingerprintCalculator fingerprints,
      Clock clock,
      UuidSource uuids,
      CatalogPaths paths,
      @Value("${hakamap.application-version:development}") String applicationVersion) {
    return new RecoverySnapshotService(
        new NioStorageFileOperations(),
        new DefensiveJsonCodec(schemas),
        new ProjectFileV1Mapper(),
        new RecoveryFileV1Validator(),
        fingerprints,
        clock,
        uuids,
        paths.recoveryDirectory(),
        paths.temporaryAssetRoot(),
        applicationVersion);
  }

  @Bean
  ProjectRecoveryCoordinator projectRecoveryCoordinator(
      OpenProjectManager openProjects,
      ProjectAssetStaging assetStaging,
      RecoverySnapshotService snapshots) {
    return new ProjectRecoveryCoordinator(openProjects, assetStaging, snapshots);
  }

  @Bean
  RecoverySchedule recoverySchedule(ProjectRecoveryCoordinator recovery) {
    return new RecoverySchedule(recovery);
  }

  static final class RecoverySchedule {
    private final ProjectRecoveryCoordinator recovery;

    private RecoverySchedule(ProjectRecoveryCoordinator recovery) {
      this.recovery = recovery;
    }

    @Scheduled(fixedDelayString = "${hakamap.recovery.poll-interval-ms:30000}")
    void writeRecoverySnapshot() {
      recovery.writeOpenProjectIfDue();
    }
  }
}
