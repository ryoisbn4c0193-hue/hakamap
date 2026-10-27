package jp.hakamap.infrastructure.lifecycle;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.WString;
import com.sun.jna.platform.win32.Guid.IID;
import com.sun.jna.platform.win32.Guid.REFIID;
import com.sun.jna.platform.win32.KnownFolders;
import com.sun.jna.platform.win32.Ole32;
import com.sun.jna.platform.win32.Shell32Util;
import com.sun.jna.platform.win32.W32Errors;
import com.sun.jna.platform.win32.WinNT.HRESULT;
import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.win32.StdCallLibrary;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Hakamap.exeを指すショートカットだけに、ウィンドウと同じ識別子を保存する。 */
final class WindowsShortcutIdentity {
  private WindowsShortcutIdentity() {}

  interface ShellFiles extends StdCallLibrary {
    HRESULT sHGetPropertyStoreFromParsingName(
        WString path, Pointer binding, int flags, REFIID iid, PointerByReference result);

    void sHChangeNotify(int event, int flags, WString path, Pointer second);
  }

  interface Properties extends StdCallLibrary {
    HRESULT pSGetPropertyKeyFromName(WString name, WindowsAppIdentity.PropertyKey result);
  }

  interface Variants extends StdCallLibrary {
    HRESULT propVariantClear(Pointer value);
  }

  static void repair(Path launcher) {
    HRESULT initialized = Ole32.INSTANCE.CoInitializeEx(null, Ole32.COINIT_APARTMENTTHREADED);
    try {
      repairShortcuts(launcher);
    } finally {
      if (W32Errors.SUCCEEDED(initialized)) {
        Ole32.INSTANCE.CoUninitialize();
      }
    }
  }

  private static void repairShortcuts(Path launcher) {
    List<Path> directories = new ArrayList<>();
    directories.add(Path.of(Shell32Util.getKnownFolderPath(KnownFolders.FOLDERID_Desktop)));
    directories.add(
        Path.of(Shell32Util.getKnownFolderPath(KnownFolders.FOLDERID_Programs)).resolve("Hakamap"));
    String roaming = System.getenv("APPDATA");
    if (roaming != null && !roaming.isBlank()) {
      directories.add(
          Path.of(
              roaming, "Microsoft", "Internet Explorer", "Quick Launch", "User Pinned", "TaskBar"));
    }
    for (Path directory : directories) {
      if (!Files.isDirectory(directory)) {
        continue;
      }
      try (var files = Files.list(directory)) {
        List<Path> shortcuts =
            files
                .filter(
                    path ->
                        path.getFileName()
                            .toString()
                            .toLowerCase(java.util.Locale.ROOT)
                            .endsWith(".lnk"))
                .toList();
        for (Path file : shortcuts) {
          repairFile(file, launcher);
        }
      } catch (IOException exception) {
        throw new LifecycleException("app-shortcut-identity-failed");
      }
    }
  }

  static void repairFile(Path file, Path launcher) {
    ShellFiles shell = Native.load("shell32", ShellFiles.class, WindowsAppIdentity.NATIVE_OPTIONS);
    PointerByReference pointer = new PointerByReference();
    HRESULT result =
        shell.sHGetPropertyStoreFromParsingName(
            new WString(file.toString()),
            null,
            2,
            new REFIID(new IID("{886D8EEB-8CF2-4446-8D02-CDBA1DBDCF99}")),
            pointer);
    if (W32Errors.FAILED(result)) {
      return;
    }
    WindowsAppIdentity.PropertyStore store =
        new WindowsAppIdentity.PropertyStore(pointer.getValue());
    try {
      String target = target(store);
      if (target == null || !target.equalsIgnoreCase(launcher.toString())) {
        return;
      }
      store.set(5, WindowsAppIdentity.APP_ID);
      store.commit();
      shell.sHChangeNotify(0x00002000, 0x0005, new WString(file.toString()), null);
    } finally {
      store.Release();
    }
  }

  private static String target(WindowsAppIdentity.PropertyStore store) {
    Properties properties =
        Native.load("propsys", Properties.class, WindowsAppIdentity.NATIVE_OPTIONS);
    WindowsAppIdentity.PropertyKey key = new WindowsAppIdentity.PropertyKey(0);
    if (W32Errors.FAILED(
        properties.pSGetPropertyKeyFromName(new WString("System.Link.TargetParsingPath"), key))) {
      return null;
    }
    try (Memory value = new Memory(Native.POINTER_SIZE == 8 ? 24 : 16)) {
      value.clear();
      if (!store.get(key, value)) {
        return null;
      }
      try {
        return value.getShort(0) == 31 && value.getPointer(8) != null
            ? value.getPointer(8).getWideString(0)
            : null;
      } finally {
        Native.load("ole32", Variants.class, WindowsAppIdentity.NATIVE_OPTIONS)
            .propVariantClear(value);
      }
    }
  }
}
