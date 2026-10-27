package jp.hakamap.infrastructure.lifecycle;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.WString;
import com.sun.jna.platform.win32.COM.Unknown;
import com.sun.jna.platform.win32.Guid.CLSID;
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
    void sHChangeNotify(int event, int flags, WString path, Pointer second);
  }

  interface Variants extends StdCallLibrary {
    HRESULT propVariantClear(Pointer value);
  }

  private static final class ComObject extends Unknown implements AutoCloseable {
    ComObject(Pointer pointer) {
      super(pointer);
    }

    void call(int slot, Object... arguments) {
      Object[] all = new Object[arguments.length + 1];
      all[0] = getPointer();
      System.arraycopy(arguments, 0, all, 1, arguments.length);
      if (W32Errors.FAILED((HRESULT) _invokeNativeObject(slot, all, HRESULT.class))) {
        throw new LifecycleException("app-shortcut-identity-failed");
      }
    }

    ComObject query(String iid) {
      PointerByReference result = new PointerByReference();
      if (W32Errors.FAILED(QueryInterface(new REFIID(new IID(iid)), result))) {
        throw new LifecycleException("app-shortcut-identity-failed");
      }
      return new ComObject(result.getValue());
    }

    @Override
    public void close() {
      Release();
    }
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

  private static ComObject load(Path file, int mode) {
    PointerByReference result = new PointerByReference();
    HRESULT created =
        Ole32.INSTANCE.CoCreateInstance(
            new CLSID("{00021401-0000-0000-C000-000000000046}"),
            null,
            1,
            new IID("{000214F9-0000-0000-C000-000000000046}"),
            result);
    if (W32Errors.FAILED(created)) {
      throw new LifecycleException("app-shortcut-identity-failed");
    }
    ComObject link = new ComObject(result.getValue());
    try (ComObject persisted = link.query("{0000010B-0000-0000-C000-000000000046}")) {
      persisted.call(5, new WString(file.toString()), mode);
      return link;
    } catch (RuntimeException exception) {
      link.close();
      throw exception;
    }
  }

  static boolean repairFile(Path file, Path launcher) {
    ComObject loaded;
    try {
      loaded = load(file, 2);
    } catch (LifecycleException exception) {
      // 破損した別アプリのショートカットでHakamapの起動を妨げない。
      return false;
    }
    try (ComObject link = loaded) {
      char[] target = new char[32768];
      link.call(3, target, target.length, null, 4);
      if (!sameLauncher(Native.toString(target), launcher)) {
        return false;
      }
      try (ComObject properties = link.query("{886D8EEB-8CF2-4446-8D02-CDBA1DBDCF99}")) {
        WindowsAppIdentity.PropertyStore store =
            new WindowsAppIdentity.PropertyStore(properties.getPointer());
        if (WindowsAppIdentity.APP_ID.equals(appId(store))) {
          return true;
        }
        store.set(5, WindowsAppIdentity.APP_ID);
        store.commit();
        if (!WindowsAppIdentity.APP_ID.equals(appId(store))) {
          throw new LifecycleException("app-shortcut-property-readback-failed");
        }
        // Shell Link自身のIPersistFileで保存し、リンク内の拡張プロパティを永続化する。
        try (ComObject persisted = link.query("{0000010B-0000-0000-C000-000000000046}")) {
          persisted.call(6, new WString(file.toString()), 1);
        }
      }
      Native.load("shell32", ShellFiles.class, WindowsAppIdentity.NATIVE_OPTIONS)
          .sHChangeNotify(0x00002000, 0x0005, new WString(file.toString()), null);
      return true;
    }
  }

  static boolean sameLauncher(String target, Path launcher) {
    try {
      // Windowsの短縮名・長い名前やジャンクションの表記差を文字列比較で拒否しない。
      return !target.isBlank() && Files.isSameFile(Path.of(target), launcher);
    } catch (IOException | java.nio.file.InvalidPathException exception) {
      return false;
    }
  }

  static String readAppId(Path file) {
    try (ComObject link = load(file, 0);
        ComObject properties = link.query("{886D8EEB-8CF2-4446-8D02-CDBA1DBDCF99}")) {
      return appId(new WindowsAppIdentity.PropertyStore(properties.getPointer()));
    }
  }

  private static String appId(WindowsAppIdentity.PropertyStore store) {
    try (Memory value = new Memory(Native.POINTER_SIZE == 8 ? 24 : 16)) {
      value.clear();
      if (!store.get(new WindowsAppIdentity.PropertyKey(5), value)) {
        return null;
      }
      try {
        return (value.getShort(0) == 31 || value.getShort(0) == 8) && value.getPointer(8) != null
            ? value.getPointer(8).getWideString(0)
            : null;
      } finally {
        Native.load("ole32", Variants.class, WindowsAppIdentity.NATIVE_OPTIONS)
            .propVariantClear(value);
      }
    }
  }
}
