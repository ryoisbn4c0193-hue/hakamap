package jp.hakamap.project.application.transfer;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import jp.hakamap.persistence.json.repository.ProjectRepository;
import jp.hakamap.project.domain.model.ProjectAggregate;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

public final class ProjectArchiveService {
  private static final int FORMAT_VERSION = 1;

  private static final int MAX_FILES = 100_010;

  private static final long MAX_MANIFEST_BYTES = 10L * 1024 * 1024;

  private static final long MAX_PROJECT_BYTES = 100L * 1024 * 1024;

  static final long MAX_TOTAL_UNCOMPRESSED_BYTES = 20L * 1024 * 1024 * 1024;

  private static final long REQUIRED_FREE_SPACE_MARGIN = 500L * 1024 * 1024;

  private static final DateTimeFormatter FILE_TIMESTAMP =
      DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssSSS'Z'")
          .withLocale(Locale.ROOT)
          .withZone(java.time.ZoneOffset.UTC);

  private final JsonMapper json =
      JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

  private final ProjectRepository projects;

  private final Clock clock;

  private final String applicationVersion;

  public ProjectArchiveService(ProjectRepository projects, Clock clock, String applicationVersion) {
    this.projects = projects;
    this.clock = clock;
    this.applicationVersion = applicationVersion;
  }

  public Path createAutomaticBackup(Path projectRoot) {
    Path directory = projectRoot.resolve("backup/automatic");
    if (latestSuccessfulDate(directory)
        .map(date -> date.equals(clock.instant().atZone(ZoneId.systemDefault()).toLocalDate()))
        .orElse(false)) {
      return null;
    }
    Path result =
        createArchive(
            projectRoot,
            directory.resolve("backup-" + FILE_TIMESTAMP.format(clock.instant()) + ".zip"),
            "backup");
    retainNewest(directory, 3);
    return result;
  }

  public Path createPreRestoreBackup(Path projectRoot) {
    return createPreRestoreBackup(projectRoot, OperationControl.NONE);
  }

  public Path createPreRestoreBackup(Path projectRoot, OperationControl control) {
    Path directory = projectRoot.resolve("backup/pre-restore");
    Path result =
        createArchive(
            projectRoot,
            directory.resolve("pre-restore-" + FILE_TIMESTAMP.format(clock.instant()) + ".zip"),
            "backup",
            control,
            false);
    retainNewest(directory, 3);
    return result;
  }

  public Path exportArchive(Path projectRoot, Path destination) {
    return exportArchive(projectRoot, destination, OperationControl.NONE);
  }

  public Path exportArchive(Path projectRoot, Path destination, OperationControl control) {
    Path target =
        destination.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".hakamap")
            ? destination
            : destination.resolveSibling(destination.getFileName() + ".hakamap");
    return createArchive(projectRoot, target, "export", control, true);
  }

  public ArchiveInspection inspect(Path archive) {
    return inspect(archive, OperationControl.NONE);
  }

  public ArchiveInspection inspect(Path archive, OperationControl control) {
    try {
      control.checkpoint();
      if (!Files.isRegularFile(archive) || Files.isSymbolicLink(archive)) {
        throw new ProjectTransferException("archive-invalid");
      }
      String archiveHash = sha256(archive, control);
      try (ZipFile zip = new ZipFile(archive.toFile())) {
        List<? extends ZipEntry> entries = zip.stream().toList();
        if (entries.size() > MAX_FILES) {
          throw new ProjectTransferException("archive-file-count-exceeded");
        }
        Set<String> names = new HashSet<>();
        Map<String, ZipEntry> byName = new HashMap<>();
        long zipTotal = 0;
        for (ZipEntry entry : entries) {
          control.checkpoint();
          String safe = safeEntryName(entry.getName());
          if (entry.getSize() < 0 || entry.getSize() > MAX_TOTAL_UNCOMPRESSED_BYTES - zipTotal) {
            throw new ProjectTransferException("archive-total-size-exceeded");
          }
          zipTotal += entry.getSize();
          if (entry.getSize() > 0
              && entry.getCompressedSize() > 0
              && entry.getSize() / entry.getCompressedSize() > 1_000) {
            throw new ProjectTransferException("archive-compression-ratio-exceeded");
          }
          if (entry.isDirectory() || !names.add(safe.toLowerCase(Locale.ROOT))) {
            throw new ProjectTransferException("archive-structure-invalid");
          }
          byName.put(safe, entry);
        }
        ZipEntry manifestEntry = byName.get("manifest.json");
        if (manifestEntry == null
            || manifestEntry.getSize() < 0
            || manifestEntry.getSize() > MAX_MANIFEST_BYTES) {
          throw new ProjectTransferException("archive-manifest-invalid");
        }
        ArchiveManifest manifest;
        try (InputStream input = limited(zip.getInputStream(manifestEntry), MAX_MANIFEST_BYTES)) {
          manifest = json.readValue(input, ArchiveManifest.class);
        }
        if (manifest.formatVersion() != FORMAT_VERSION
            || manifest.projectId() == null
            || manifest.files() == null) {
          throw new ProjectTransferException("archive-version-unsupported");
        }
        Set<String> declared = new HashSet<>();
        long declaredTotal = 0;
        for (ArchiveFile file : manifest.files()) {
          control.checkpoint();
          String path = safeEntryName(file.path());
          ZipEntry entry = byName.get(path);
          if (file.sizeBytes() < 0
              || file.sizeBytes() > MAX_TOTAL_UNCOMPRESSED_BYTES - declaredTotal) {
            throw new ProjectTransferException("archive-total-size-exceeded");
          }
          declaredTotal += file.sizeBytes();
          if (!declared.add(path.toLowerCase(Locale.ROOT))
              || entry == null
              || entry.getSize() != file.sizeBytes()
              || !sha256(zip.getInputStream(entry), control).equals(file.sha256())) {
            throw new ProjectTransferException("archive-integrity-invalid");
          }
        }
        if (declared.size() + 1 != byName.size() || !declared.contains("project.json")) {
          throw new ProjectTransferException("archive-manifest-mismatch");
        }
        return new ArchiveInspection(
            manifest.projectId(),
            manifest.projectName(),
            manifest.createdAt(),
            manifest.applicationVersion(),
            Files.size(archive),
            archiveHash,
            Files.getLastModifiedTime(archive));
      }
    } catch (IOException exception) {
      throw new ProjectTransferException("archive-invalid", exception);
    }
  }

  public ExtractedProject extractAndValidate(Path archive, Path destination) {
    return extractAndValidate(archive, destination, OperationControl.NONE);
  }

  public ExtractedProject extractAndValidate(
      Path archive, Path destination, OperationControl control) {
    return extractAndValidate(archive, destination, control, 0);
  }

  public ExtractedProject extractAndValidate(
      Path archive, Path destination, OperationControl control, long additionalBytes) {
    control.checkpoint();
    ArchiveInspection inspection = inspect(archive, control);
    try {
      long declaredTotal = declaredTotal(archive);
      Path usableRoot =
          destination.toAbsolutePath().normalize().getParent() == null
              ? destination.toAbsolutePath().normalize()
              : destination.toAbsolutePath().normalize().getParent();
      if (additionalBytes < 0
          || declaredTotal > Long.MAX_VALUE - additionalBytes
          || declaredTotal + additionalBytes > Long.MAX_VALUE - REQUIRED_FREE_SPACE_MARGIN
          || Files.getFileStore(usableRoot).getUsableSpace()
              < declaredTotal + additionalBytes + REQUIRED_FREE_SPACE_MARGIN) {
        throw new ProjectTransferException("archive-space-insufficient");
      }
      Files.createDirectories(destination);
      try (ZipFile zip = new ZipFile(archive.toFile())) {
        ArchiveManifest manifest;
        ZipEntry manifestEntry = zip.getEntry("manifest.json");
        try (InputStream input = limited(zip.getInputStream(manifestEntry), MAX_MANIFEST_BYTES)) {
          manifest = json.readValue(input, ArchiveManifest.class);
        }
        for (ArchiveFile file : manifest.files()) {
          control.checkpoint();
          Path target = destination.resolve(file.path()).normalize();
          if (!target.startsWith(destination.toAbsolutePath().normalize())) {
            throw new ProjectTransferException("archive-path-invalid");
          }
          Files.createDirectories(target.getParent());
          try (InputStream input = zip.getInputStream(zip.getEntry(file.path()));
              OutputStream output = new BufferedOutputStream(Files.newOutputStream(target))) {
            copyExactly(input, output, file.sizeBytes(), control);
          }
        }
      }
      ProjectAggregate project = projects.read(destination);
      if (!project.metadata().id().value().equals(inspection.projectId())) {
        throw new ProjectTransferException("archive-project-mismatch");
      }
      return new ExtractedProject(project, destination, inspection);
    } catch (IOException | RuntimeException exception) {
      deleteTreeQuietly(destination);
      if (exception instanceof ProjectTransferException transfer) {
        throw transfer;
      }
      throw new ProjectTransferException("archive-extract-failed", exception);
    }
  }

  private long declaredTotal(Path archive) throws IOException {
    try (ZipFile zip = new ZipFile(archive.toFile())) {
      ZipEntry manifestEntry = zip.getEntry("manifest.json");
      try (InputStream input = limited(zip.getInputStream(manifestEntry), MAX_MANIFEST_BYTES)) {
        ArchiveManifest manifest = json.readValue(input, ArchiveManifest.class);
        long total = 0;
        for (ArchiveFile file : manifest.files()) {
          if (file.sizeBytes() < 0 || file.sizeBytes() > MAX_TOTAL_UNCOMPRESSED_BYTES - total) {
            throw new ProjectTransferException("archive-total-size-exceeded");
          }
          total += file.sizeBytes();
        }
        return total;
      }
    }
  }

  private void copyExactly(
      InputStream input, OutputStream output, long expected, OperationControl control)
      throws IOException {
    byte[] buffer = new byte[64 * 1024];
    long total = 0;
    while (true) {
      control.checkpoint();
      int read = input.read(buffer);
      if (read < 0) {
        break;
      }
      if (read > expected - total) {
        throw new ProjectTransferException("archive-size-mismatch");
      }
      output.write(buffer, 0, read);
      total += read;
    }
    if (total != expected) {
      throw new ProjectTransferException("archive-size-mismatch");
    }
  }

  private Path createArchive(Path projectRoot, Path target, String archiveType) {
    return createArchive(projectRoot, target, archiveType, OperationControl.NONE, false);
  }

  private Path createArchive(
      Path projectRoot,
      Path target,
      String archiveType,
      OperationControl control,
      boolean markCommit) {
    Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
    try {
      control.checkpoint();
      List<Path> files = projectFiles(projectRoot);
      ProjectAggregate project = projects.read(projectRoot);
      List<ArchiveFile> manifestFiles = new ArrayList<>();
      for (Path file : files) {
        control.checkpoint();
        manifestFiles.add(
            new ArchiveFile(
                projectRoot.relativize(file).toString().replace('\\', '/'),
                Files.size(file),
                sha256(file, control)));
      }
      ArchiveManifest manifest =
          new ArchiveManifest(
              FORMAT_VERSION,
              archiveType,
              applicationVersion,
              clock.instant(),
              project.metadata().id().value(),
              project.metadata().name().value(),
              List.copyOf(manifestFiles));
      Files.createDirectories(target.toAbsolutePath().normalize().getParent());
      try (ZipOutputStream zip =
          new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(temporary)))) {
        writeEntry(zip, "manifest.json", json.writeValueAsBytes(manifest));
        for (Path file : files) {
          control.checkpoint();
          String relative = projectRoot.relativize(file).toString().replace('\\', '/');
          zip.putNextEntry(new ZipEntry(relative));
          copy(file, zip, control);
          zip.closeEntry();
        }
      }
      control.checkpoint();
      inspect(temporary, control);
      if (markCommit) {
        control.beginCommit();
      } else {
        control.checkpoint();
      }
      move(temporary, target);
      return target;
    } catch (IOException | RuntimeException exception) {
      deleteTreeQuietly(temporary);
      if (exception instanceof ProjectTransferException transfer) {
        throw transfer;
      }
      throw new ProjectTransferException("archive-create-failed", exception);
    }
  }

  private void copy(Path source, OutputStream output, OperationControl control) throws IOException {
    try (InputStream input = Files.newInputStream(source)) {
      byte[] buffer = new byte[64 * 1024];
      while (true) {
        control.checkpoint();
        int read = input.read(buffer);
        if (read < 0) {
          return;
        }
        output.write(buffer, 0, read);
      }
    }
  }

  private List<Path> projectFiles(Path root) throws IOException {
    List<Path> files = new ArrayList<>();
    Path project = root.resolve("project.json");
    if (!Files.isRegularFile(project) || Files.isSymbolicLink(project)) {
      throw new ProjectTransferException("archive-project-missing");
    }
    if (Files.size(project) > MAX_PROJECT_BYTES) {
      throw new ProjectTransferException("archive-project-size-exceeded");
    }
    files.add(project);
    Path assets = root.resolve("assets");
    if (Files.exists(assets)) {
      try (var stream = Files.walk(assets)) {
        stream
            .filter(Files::isRegularFile)
            .filter(path -> !Files.isSymbolicLink(path))
            .sorted()
            .forEach(files::add);
      }
    }
    if (files.size() + 1 > MAX_FILES) {
      throw new ProjectTransferException("archive-file-count-exceeded");
    }
    return List.copyOf(files);
  }

  private void retainNewest(Path directory, int count) {
    if (!Files.isDirectory(directory)) {
      return;
    }
    try (var stream = Files.list(directory)) {
      List<Path> archives =
          stream
              .filter(path -> path.getFileName().toString().endsWith(".zip"))
              .sorted(Comparator.comparing(this::modified).reversed())
              .toList();
      for (Path old : archives.stream().skip(count).toList()) {
        Files.deleteIfExists(old);
      }
    } catch (IOException exception) {
      throw new ProjectTransferException("archive-retention-failed", exception);
    }
  }

  private java.util.Optional<java.time.LocalDate> latestSuccessfulDate(Path directory) {
    if (!Files.isDirectory(directory)) {
      return java.util.Optional.empty();
    }
    try (var stream = Files.list(directory)) {
      return stream
          .filter(path -> path.getFileName().toString().endsWith(".zip"))
          .flatMap(
              path -> {
                try {
                  return java.util.stream.Stream.of(inspect(path).createdAt());
                } catch (RuntimeException exception) {
                  return java.util.stream.Stream.empty();
                }
              })
          .max(Comparator.naturalOrder())
          .map(time -> time.atZone(ZoneId.systemDefault()).toLocalDate());
    } catch (IOException exception) {
      throw new ProjectTransferException("archive-list-failed", exception);
    }
  }

  private FileTime modified(Path path) {
    try {
      return Files.getLastModifiedTime(path);
    } catch (IOException exception) {
      throw new ProjectTransferException("archive-list-failed", exception);
    }
  }

  private String safeEntryName(String name) {
    if (name == null
        || name.isBlank()
        || name.startsWith("/")
        || name.startsWith("\\")
        || name.contains("\\")
        || Path.of(name).isAbsolute()
        || Path.of(name).normalize().startsWith("..")) {
      throw new ProjectTransferException("archive-path-invalid");
    }
    return name;
  }

  private InputStream limited(InputStream source, long limit) {
    return new java.io.FilterInputStream(source) {
      private long read;

      @Override
      public int read() throws IOException {
        if (read >= limit) {
          throw new IOException("limit");
        }
        int value = super.read();
        if (value >= 0) {
          read++;
        }
        return value;
      }

      @Override
      public int read(byte[] bytes, int offset, int length) throws IOException {
        if (read >= limit) {
          throw new IOException("limit");
        }
        int value = super.read(bytes, offset, (int) Math.min(length, limit - read));
        if (value > 0) {
          read += value;
        }
        return value;
      }
    };
  }

  private String sha256(Path path) {
    return sha256(path, OperationControl.NONE);
  }

  private String sha256(Path path, OperationControl control) {
    try (InputStream input = Files.newInputStream(path)) {
      return sha256(input, control);
    } catch (IOException exception) {
      throw new ProjectTransferException("archive-integrity-invalid", exception);
    }
  }

  private String sha256(InputStream input) {
    return sha256(input, OperationControl.NONE);
  }

  private String sha256(InputStream input, OperationControl control) {
    try (InputStream source = new BufferedInputStream(input)) {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] buffer = new byte[64 * 1024];
      while (true) {
        control.checkpoint();
        int read = source.read(buffer);
        if (read < 0) {
          break;
        }
        digest.update(buffer, 0, read);
      }
      return HexFormat.of().formatHex(digest.digest());
    } catch (IOException | NoSuchAlgorithmException exception) {
      throw new ProjectTransferException("archive-integrity-invalid", exception);
    }
  }

  private void writeEntry(ZipOutputStream zip, String name, byte[] content) throws IOException {
    zip.putNextEntry(new ZipEntry(name));
    zip.write(content);
    zip.closeEntry();
  }

  private void move(Path source, Path target) throws IOException {
    try {
      Files.move(
          source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (AtomicMoveNotSupportedException exception) {
      throw new ProjectTransferException("archive-atomic-move-unsupported", exception);
    }
  }

  public static void deleteTreeQuietly(Path root) {
    if (root == null || !Files.exists(root)) {
      return;
    }
    try (var stream = Files.walk(root)) {
      stream
          .sorted(Comparator.reverseOrder())
          .forEach(
              path -> {
                try {
                  Files.deleteIfExists(path);
                } catch (IOException ignored) {
                  // 次回起動時の清掃対象として残す。
                }
              });
    } catch (IOException ignored) {
      // 次回起動時の清掃対象として残す。
    }
  }

  public record ArchiveManifest(
      int formatVersion,
      String archiveType,
      String applicationVersion,
      Instant createdAt,
      UUID projectId,
      String projectName,
      List<ArchiveFile> files) {}

  public record ArchiveFile(String path, long sizeBytes, String sha256) {}

  public record ArchiveInspection(
      UUID projectId,
      String projectName,
      Instant createdAt,
      String applicationVersion,
      long sizeBytes,
      String archiveSha256,
      FileTime lastModified) {}

  public record ExtractedProject(
      ProjectAggregate project, Path directory, ArchiveInspection inspection) {}
}
