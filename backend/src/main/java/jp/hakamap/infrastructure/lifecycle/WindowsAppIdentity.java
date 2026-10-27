package jp.hakamap.infrastructure.lifecycle;

import com.sun.jna.FunctionMapper;
import com.sun.jna.Library;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.Structure.FieldOrder;
import com.sun.jna.WString;
import com.sun.jna.platform.win32.COM.Unknown;
import com.sun.jna.platform.win32.Guid.GUID;
import com.sun.jna.platform.win32.Guid.IID;
import com.sun.jna.platform.win32.Guid.REFIID;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.Ole32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.W32Errors;
import com.sun.jna.platform.win32.WinDef.DWORD;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.LPARAM;
import com.sun.jna.platform.win32.WinDef.WPARAM;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.platform.win32.WinNT.HRESULT;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.win32.StdCallLibrary;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** タスクバーの識別・ピン留め時の再起動先をウィンドウに設定する。 */
final class WindowsAppIdentity implements WindowsAppBrowser.Windows {
  static final String APP_ID = "jp.hakamap.Desktop";

  static final Map<String, ?> NATIVE_OPTIONS =
      Map.of(
          Library.OPTION_FUNCTION_MAPPER,
          (FunctionMapper)
              (library, method) ->
                  Character.toUpperCase(method.getName().charAt(0))
                      + method.getName().substring(1));

  private final User32 user = User32.INSTANCE;

  interface ShellProperties extends StdCallLibrary {
    HRESULT sHGetPropertyStoreForWindow(HWND window, REFIID iid, PointerByReference result);
  }

  @FieldOrder({"format", "id"})
  public static class PropertyKey extends Structure {
    public GUID format = new GUID("{9F4C2855-9F79-4B39-A8D0-E1D42DE1D5F3}");

    public int id;

    PropertyKey(int id) {
      this.id = id;
      write();
    }
  }

  static final class PropertyStore extends Unknown {
    PropertyStore(Pointer pointer) {
      super(pointer);
    }

    boolean get(PropertyKey key, Pointer value) {
      return W32Errors.SUCCEEDED(
          (HRESULT)
              _invokeNativeObject(
                  5, new Object[] {getPointer(), key.getPointer(), value}, HRESULT.class));
    }

    void commit() {
      HRESULT result = (HRESULT) _invokeNativeObject(7, new Object[] {getPointer()}, HRESULT.class);
      if (W32Errors.FAILED(result)) {
        throw new LifecycleException("app-shortcut-identity-failed");
      }
    }

    void set(int property, String text) {
      // PROPVARIANTのVT_LPWSTR。文字列はSetValueが戻るまで呼び出し側が保持する。
      try (Memory value = new Memory(Native.POINTER_SIZE == 8 ? 24 : 16);
          Memory string = new Memory((text.length() + 1L) * Native.WCHAR_SIZE)) {
        value.clear();
        value.setShort(0, (short) 31);
        string.setWideString(0, text);
        value.setPointer(8, string);
        PropertyKey key = new PropertyKey(property);
        HRESULT result =
            (HRESULT)
                _invokeNativeObject(
                    6, new Object[] {getPointer(), key.getPointer(), value}, HRESULT.class);
        if (W32Errors.FAILED(result)) {
          throw new LifecycleException("app-window-identity-failed");
        }
      }
    }
  }

  private HWND handle(long window) {
    return new HWND(Pointer.createConstant(window));
  }

  @Override
  public boolean isWindow(long window) {
    HWND target = handle(window);
    HANDLE marker = ExtraUser.INSTANCE.getPropW(target, new WString(APP_ID));
    return user.IsWindow(target) && marker != null && Pointer.nativeValue(marker.getPointer()) == 1;
  }

  @Override
  public Set<Long> handles() {
    Set<Long> result = new HashSet<>();
    user.EnumWindows(
        (window, parameter) -> {
          result.add(Pointer.nativeValue(window.getPointer()));
          return true;
        },
        null);
    return result;
  }

  @Override
  public Optional<Long> find(Set<Long> processIds, Set<Long> excluded) {
    long[] result = {0};
    user.EnumWindows(
        (window, parameter) -> {
          long value = Pointer.nativeValue(window.getPointer());
          IntByReference pid = new IntByReference();
          user.GetWindowThreadProcessId(window, pid);
          if (!excluded.contains(value)
              && processIds.contains(Integer.toUnsignedLong(pid.getValue()))
              && user.IsWindowVisible(window)
              && hasApplicationTitle(window)) {
            result[0] = value;
            return false;
          }
          return true;
        },
        null);
    return result[0] == 0 ? Optional.empty() : Optional.of(result[0]);
  }

  private boolean hasApplicationTitle(HWND window) {
    char[] title = new char[256];
    user.GetWindowText(window, title, title.length);
    return Native.toString(title).startsWith("Hakamap");
  }

  @Override
  public void identify(long window, Path launcher, Path icon) {
    if (!ExtraUser.INSTANCE.setPropW(
        handle(window), new WString(APP_ID), new HANDLE(Pointer.createConstant(1)))) {
      throw new LifecycleException("app-window-identity-failed");
    }
    HRESULT initialized = Ole32.INSTANCE.CoInitializeEx(null, Ole32.COINIT_APARTMENTTHREADED);
    boolean uninitialize = W32Errors.SUCCEEDED(initialized);
    try {
      ShellProperties shell = Native.load("shell32", ShellProperties.class, NATIVE_OPTIONS);
      PointerByReference pointer = new PointerByReference();
      HRESULT result =
          shell.sHGetPropertyStoreForWindow(
              handle(window),
              new REFIID(new IID("{886D8EEB-8CF2-4446-8D02-CDBA1DBDCF99}")),
              pointer);
      if (W32Errors.FAILED(result)) {
        throw new LifecycleException("app-window-identity-failed");
      }
      PropertyStore store = new PropertyStore(pointer.getValue());
      try {
        // 再起動情報を先に設定し、最後にAppUserModelIDを変更してグループ化する。
        store.set(2, "\"" + launcher + "\"");
        store.set(3, icon + ",0");
        store.set(4, "Hakamap");
        store.set(5, APP_ID);
      } finally {
        store.Release();
      }
    } finally {
      if (uninitialize) {
        Ole32.INSTANCE.CoUninitialize();
      }
    }
  }

  @Override
  public Set<Long> ownedProcesses() {
    Set<Long> result = new HashSet<>();
    user.EnumWindows(
        (window, parameter) -> {
          HANDLE marker = ExtraUser.INSTANCE.getPropW(window, new WString(APP_ID));
          if (marker != null && Pointer.nativeValue(marker.getPointer()) == 1) {
            IntByReference pid = new IntByReference();
            user.GetWindowThreadProcessId(window, pid);
            result.add(Integer.toUnsignedLong(pid.getValue()));
          }
          return true;
        },
        null);
    return result;
  }

  @Override
  public void close(long window) {
    user.PostMessage(handle(window), 0x0010, new WPARAM(0), new LPARAM(0));
  }

  @Override
  public boolean activate(long window) {
    HWND target = handle(window);
    // SW_RESTOREは最大化を解除するため、最小化した場合だけ使用する。
    if (ExtraUser.INSTANCE.isIconic(target)) {
      user.ShowWindow(target, 9);
    }
    if (user.SetForegroundWindow(target)) {
      return true;
    }
    HWND foreground = user.GetForegroundWindow();
    if (foreground == null) {
      return false;
    }
    int targetThread = Kernel32.INSTANCE.GetCurrentThreadId();
    int foregroundThread = user.GetWindowThreadProcessId(foreground, null);
    if (targetThread == foregroundThread) {
      return user.SetForegroundWindow(target);
    }
    DWORD own = new DWORD(Integer.toUnsignedLong(targetThread));
    DWORD other = new DWORD(Integer.toUnsignedLong(foregroundThread));
    if (!user.AttachThreadInput(own, other, true)) {
      return false;
    }
    try {
      user.BringWindowToTop(target);
      return user.SetForegroundWindow(target);
    } finally {
      user.AttachThreadInput(own, other, false);
    }
  }

  static void allowActivation(long processId) {
    if (System.getProperty("os.name", "").startsWith("Windows")) {
      ExtraUser.INSTANCE.allowSetForegroundWindow((int) processId);
    }
  }

  interface ExtraUser extends StdCallLibrary {
    ExtraUser INSTANCE = Native.load("user32", ExtraUser.class, NATIVE_OPTIONS);

    boolean isIconic(HWND window);

    boolean allowSetForegroundWindow(int processId);

    HANDLE getPropW(HWND window, WString name);

    boolean setPropW(HWND window, WString name, HANDLE data);
  }
}
