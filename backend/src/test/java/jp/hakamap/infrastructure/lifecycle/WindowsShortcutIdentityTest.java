package jp.hakamap.infrastructure.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.jna.Pointer;
import com.sun.jna.WString;
import com.sun.jna.platform.win32.COM.Unknown;
import com.sun.jna.platform.win32.Guid.CLSID;
import com.sun.jna.platform.win32.Guid.IID;
import com.sun.jna.platform.win32.Guid.REFIID;
import com.sun.jna.platform.win32.Ole32;
import com.sun.jna.platform.win32.W32Errors;
import com.sun.jna.platform.win32.WinNT.HRESULT;
import com.sun.jna.ptr.PointerByReference;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

@EnabledOnOs(OS.WINDOWS)
class WindowsShortcutIdentityTest {
  @TempDir Path directory;

  private static final class ComObject extends Unknown {
    ComObject(Pointer pointer) {
      super(pointer);
    }

    void call(int slot, Object... arguments) {
      Object[] all = new Object[arguments.length + 1];
      all[0] = getPointer();
      System.arraycopy(arguments, 0, all, 1, arguments.length);
      assertThat(W32Errors.SUCCEEDED((HRESULT) _invokeNativeObject(slot, all, HRESULT.class)))
          .isTrue();
    }
  }

  @Test
  void updatesOnlyShortcutsTargetingTheInstalledLauncher() throws Exception {
    HRESULT initialized = Ole32.INSTANCE.CoInitializeEx(null, Ole32.COINIT_APARTMENTTHREADED);
    try {
      Path launcher = Files.createFile(directory.resolve("Hakamap.exe"));
      Path other = Files.createFile(directory.resolve("Other.exe"));
      Path ownLink = directory.resolve("Hakamap.lnk");
      Path otherLink = directory.resolve("Other.lnk");
      createLink(ownLink, launcher);
      createLink(otherLink, other);
      byte[] previous = Files.readAllBytes(otherLink);
      assertThat(WindowsShortcutIdentity.repairFile(ownLink, launcher))
          .as("Hakamapのリンク対象を識別する")
          .isTrue();
      assertThat(WindowsShortcutIdentity.repairFile(otherLink, launcher)).isFalse();
      assertThat(Files.readAllBytes(otherLink)).isEqualTo(previous);
      assertThat(appId(ownLink)).isEqualTo(WindowsAppIdentity.APP_ID);
    } finally {
      if (W32Errors.SUCCEEDED(initialized)) {
        Ole32.INSTANCE.CoUninitialize();
      }
    }
  }

  private void createLink(Path file, Path target) {
    PointerByReference pointer = new PointerByReference();
    HRESULT created =
        Ole32.INSTANCE.CoCreateInstance(
            new CLSID("{00021401-0000-0000-C000-000000000046}"),
            null,
            1,
            new IID("{000214F9-0000-0000-C000-000000000046}"),
            pointer);
    assertThat(W32Errors.SUCCEEDED(created)).isTrue();
    ComObject link = new ComObject(pointer.getValue());
    try {
      link.call(20, new WString(target.toString()));
      PointerByReference persistence = new PointerByReference();
      assertThat(
              W32Errors.SUCCEEDED(
                  link.QueryInterface(
                      new REFIID(new IID("{0000010B-0000-0000-C000-000000000046}")), persistence)))
          .isTrue();
      ComObject persisted = new ComObject(persistence.getValue());
      try {
        persisted.call(6, new WString(file.toString()), 1);
      } finally {
        persisted.Release();
      }
    } finally {
      link.Release();
    }
  }

  private String appId(Path file) {
    return WindowsShortcutIdentity.readAppId(file);
  }
}
