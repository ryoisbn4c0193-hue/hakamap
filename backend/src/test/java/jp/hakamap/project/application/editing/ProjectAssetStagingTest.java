package jp.hakamap.project.application.editing;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectAssetStagingTest {
  @TempDir Path temporaryDirectory;

  @Test
  void discardsOrphanedFilesAndDirectoriesAfterProcessRestart() throws IOException {
    UUID projectId = UUID.randomUUID();
    Path stagingRoot = temporaryDirectory.resolve("temporary-assets");
    Path projectRoot = stagingRoot.resolve(projectId.toString());
    Files.createDirectories(projectRoot.resolve("conversion/nested"));
    Files.writeString(projectRoot.resolve("orphan.png"), "orphan");
    Files.writeString(projectRoot.resolve("conversion/nested/converted.png"), "converted");

    new ProjectAssetStaging(stagingRoot).discardStrict(projectId);

    assertThat(projectRoot).doesNotExist();
  }
}
