package jp.hakamap.persistence.json.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jp.hakamap.persistence.json.DefensiveJsonCodec;
import jp.hakamap.persistence.json.JsonPersistenceException;
import jp.hakamap.persistence.json.PersistenceTestFixtures;
import jp.hakamap.persistence.json.mapper.ProjectFileV1Mapper;
import jp.hakamap.persistence.json.model.catalog.ActiveCatalogProjectV1;
import jp.hakamap.persistence.json.model.catalog.CatalogFileV1;
import jp.hakamap.persistence.json.model.recovery.RecoveryFileV1;
import jp.hakamap.persistence.json.model.recovery.StagedAssetV1;
import jp.hakamap.persistence.json.validation.CatalogFileV1Validator;
import jp.hakamap.persistence.json.validation.ProjectAssetFileValidator;
import jp.hakamap.persistence.json.validation.RecoveryFileV1Validator;
import jp.hakamap.project.domain.model.AssetMetadata;
import jp.hakamap.project.domain.model.ProjectAggregate;
import jp.hakamap.project.domain.value.AssetId;
import jp.hakamap.project.domain.value.AssetType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileRepositoryTest {
  private static final UUID SECOND_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");

  private final DefensiveJsonCodec codec = PersistenceTestFixtures.codec();

  @TempDir Path temporaryDirectory;

  @Test
  void writesAndReadsProject() throws Exception {
    FileProjectRepository repository =
        new FileProjectRepository(
            codec, new ProjectFileV1Mapper(), new ProjectAssetFileValidator());
    Path root = temporaryDirectory.resolve("project");

    repository.write(root, PersistenceTestFixtures.emptyProject());
    byte[] first = Files.readAllBytes(root.resolve("project.json"));
    repository.write(root, PersistenceTestFixtures.emptyProject());

    assertThat(Files.readAllBytes(root.resolve("project.json"))).isEqualTo(first);
    assertThat(repository.read(root).metadata())
        .isEqualTo(PersistenceTestFixtures.emptyProject().metadata());
  }

  @Test
  void writesCatalogInUuidOrderAndReadsIt() throws Exception {
    FileCatalogRepository repository =
        new FileCatalogRepository(codec, new CatalogFileV1Validator());
    CatalogFileV1 catalog =
        new CatalogFileV1(
            1,
            PersistenceTestFixtures.PROJECT_ID,
            List.of(
                active(SECOND_ID, "C:\\墓地\\二"),
                active(PersistenceTestFixtures.PROJECT_ID, "C:\\墓地\\一")));
    Path path = temporaryDirectory.resolve("catalog.json");

    repository.write(path, catalog);

    assertThat(repository.read(path).projects())
        .extracting(project -> project.projectId())
        .containsExactly(PersistenceTestFixtures.PROJECT_ID, SECOND_ID);
    assertThat(Files.readString(path))
        .contains("\"lastKnownCreatedAt\" : \"2026-01-02T03:04:05.006Z\"");
  }

  @Test
  void acceptsUncPathForSmbShare() {
    FileCatalogRepository repository =
        new FileCatalogRepository(codec, new CatalogFileV1Validator());
    CatalogFileV1 catalog =
        new CatalogFileV1(
            1, null, List.of(active(PersistenceTestFixtures.PROJECT_ID, "\\\\server\\share\\墓地")));
    Path path = temporaryDirectory.resolve("unc-catalog.json");

    repository.write(path, catalog);

    assertThat(repository.read(path)).isEqualTo(catalog);
  }

  @Test
  void rejectsDuplicateCatalogPathAndInvalidDefault() {
    FileCatalogRepository repository =
        new FileCatalogRepository(codec, new CatalogFileV1Validator());
    CatalogFileV1 duplicatePath =
        new CatalogFileV1(
            1,
            null,
            List.of(
                active(PersistenceTestFixtures.PROJECT_ID, "C:\\墓地\\一"),
                active(SECOND_ID, "c:\\墓地\\.\\一")));
    CatalogFileV1 missingDefault =
        new CatalogFileV1(
            1, SECOND_ID, List.of(active(PersistenceTestFixtures.PROJECT_ID, "C:\\墓地\\一")));

    assertIntegrityError(
        () -> repository.write(temporaryDirectory.resolve("one.json"), duplicatePath));
    assertIntegrityError(
        () -> repository.write(temporaryDirectory.resolve("two.json"), missingDefault));
  }

  @Test
  void writesRecoveryAndRejectsProjectMismatch() {
    FileRecoveryRepository repository =
        new FileRecoveryRepository(
            temporaryDirectory.resolve("temp-assets"), codec, new RecoveryFileV1Validator());
    RecoveryFileV1 recovery =
        new RecoveryFileV1(
            1,
            "0.0.1",
            PersistenceTestFixtures.PROJECT_ID,
            PersistenceTestFixtures.CREATED_AT,
            "0".repeat(64),
            PersistenceTestFixtures.emptyProjectFile(),
            List.of());
    Path path = temporaryDirectory.resolve("recovery.json");

    repository.write(path, recovery);

    assertThat(repository.read(path)).isEqualTo(recovery);
    RecoveryFileV1 mismatch =
        new RecoveryFileV1(
            1,
            "0.0.1",
            SECOND_ID,
            PersistenceTestFixtures.CREATED_AT,
            "0".repeat(64),
            PersistenceTestFixtures.emptyProjectFile(),
            List.of());
    assertThatThrownBy(() -> repository.write(path, mismatch))
        .isInstanceOfSatisfying(
            JsonPersistenceException.class,
            exception -> assertThat(exception.code()).isEqualTo("recovery-integrity-invalid"));
  }

  @Test
  void validatesProjectAssetContentAndHash() throws Exception {
    FileProjectRepository repository =
        new FileProjectRepository(
            codec, new ProjectFileV1Mapper(), new ProjectAssetFileValidator());
    Path root = temporaryDirectory.resolve("asset-project");
    UUID assetId = UUID.fromString("33333333-3333-4333-8333-333333333333");
    byte[] pngHeader =
        new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0, 0, 0, 0};
    Path assetPath = root.resolve("assets/backgrounds/" + assetId + ".png");
    Files.createDirectories(assetPath.getParent());
    Files.write(assetPath, pngHeader);
    ProjectAggregate valid = projectWithBackgroundAsset(assetId, sha256(pngHeader));

    repository.write(root, valid);

    assertThat(repository.read(root).assets()).hasSize(1);
    ProjectAggregate invalid = projectWithBackgroundAsset(assetId, "0".repeat(64));
    assertThatThrownBy(() -> repository.write(root, invalid))
        .isInstanceOfSatisfying(
            JsonPersistenceException.class,
            exception -> assertThat(exception.code()).isEqualTo("asset-integrity-invalid"));
  }

  @Test
  void validatesRecoveryStagedAssetContent() throws Exception {
    Path temporaryAssets = temporaryDirectory.resolve("recovery-temp");
    FileRecoveryRepository repository =
        new FileRecoveryRepository(temporaryAssets, codec, new RecoveryFileV1Validator());
    UUID assetId = UUID.fromString("33333333-3333-4333-8333-333333333333");
    byte[] pngHeader =
        new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0, 0, 0, 0};
    String hash = sha256(pngHeader);
    String relativePath = "44444444-4444-4444-8444-444444444444/" + assetId + ".png";
    Path stagedFile = temporaryAssets.resolve(relativePath);
    Files.createDirectories(stagedFile.getParent());
    Files.write(stagedFile, pngHeader);
    RecoveryFileV1 recovery =
        new RecoveryFileV1(
            1,
            "0.0.1",
            PersistenceTestFixtures.PROJECT_ID,
            PersistenceTestFixtures.CREATED_AT,
            "0".repeat(64),
            new ProjectFileV1Mapper().toFile(projectWithBackgroundAsset(assetId, hash)),
            List.of(new StagedAssetV1(assetId, relativePath, pngHeader.length, hash)));

    repository.write(temporaryDirectory.resolve("recovery-with-asset.json"), recovery);
    Files.delete(stagedFile);

    assertThatThrownBy(
            () -> repository.read(temporaryDirectory.resolve("recovery-with-asset.json")))
        .isInstanceOfSatisfying(
            JsonPersistenceException.class,
            exception -> assertThat(exception.code()).isEqualTo("recovery-integrity-invalid"));
  }

  private ActiveCatalogProjectV1 active(UUID id, String path) {
    return new ActiveCatalogProjectV1(
        id,
        path,
        "墓地",
        PersistenceTestFixtures.CREATED_AT,
        PersistenceTestFixtures.CREATED_AT,
        "active");
  }

  private ProjectAggregate projectWithBackgroundAsset(UUID assetId, String sha256) {
    AssetMetadata asset =
        new AssetMetadata(
            new AssetId(assetId),
            AssetType.BACKGROUND,
            Optional.empty(),
            "background.png",
            "assets/backgrounds/" + assetId + ".png",
            "image/png",
            "image/png",
            12,
            sha256,
            PersistenceTestFixtures.CREATED_AT,
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty());
    ProjectAggregate base = PersistenceTestFixtures.emptyProject();
    return new ProjectAggregate(
        base.metadata(), Optional.empty(), List.of(), List.of(), List.of(), List.of(asset));
  }

  private String sha256(byte[] bytes) throws Exception {
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
  }

  private void assertIntegrityError(
      org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
    assertThatThrownBy(callable)
        .isInstanceOfSatisfying(
            JsonPersistenceException.class,
            exception -> assertThat(exception.code()).isEqualTo("catalog-integrity-invalid"));
  }
}
