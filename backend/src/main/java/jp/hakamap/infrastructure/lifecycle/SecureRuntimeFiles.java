package jp.hakamap.infrastructure.lifecycle;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.UserPrincipal;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

final class SecureRuntimeFiles {
  private SecureRuntimeFiles() {}

  static void secureDirectory(Path directory) {
    try {
      Files.createDirectories(directory);
      applyOwnerOnlyPermissions(directory);
    } catch (IOException | UnsupportedOperationException exception) {
      throw new LifecycleException("runtime-directory-unavailable", exception);
    }
  }

  static void secureFile(Path file) {
    try {
      applyOwnerOnlyPermissions(file);
    } catch (IOException | UnsupportedOperationException exception) {
      throw new LifecycleException("runtime-file-permission-failed", exception);
    }
  }

  private static void applyOwnerOnlyPermissions(Path path) throws IOException {
    AclFileAttributeView acl = Files.getFileAttributeView(path, AclFileAttributeView.class);
    if (acl != null) {
      UserPrincipal owner = Files.getOwner(path);
      Set<AclEntryPermission> permissions = EnumSet.allOf(AclEntryPermission.class);
      AclEntry entry =
          AclEntry.newBuilder()
              .setType(AclEntryType.ALLOW)
              .setPrincipal(owner)
              .setPermissions(permissions)
              .build();
      acl.setAcl(List.of(entry));
      return;
    }
    Files.setPosixFilePermissions(
        path,
        Files.isDirectory(path)
            ? EnumSet.of(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.OWNER_EXECUTE)
            : EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
  }
}
