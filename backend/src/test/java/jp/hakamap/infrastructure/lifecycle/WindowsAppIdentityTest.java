package jp.hakamap.infrastructure.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

@EnabledOnOs(OS.WINDOWS)
class WindowsAppIdentityTest {
  @Test
  void writesRealWindowIdentityAndRecognizesItsOwningProcess() {
    // AWTのheadless設定に依存せず、一時的な非表示のWin32ウィンドウで検査する。
    User32 user = User32.INSTANCE;
    HWND handle =
        user.CreateWindowEx(0, "STATIC", "Hakamap 検査用", 0, 0, 0, 320, 160, null, null, null, null);
    assertThat(handle).isNotNull();
    try {
      WindowsAppIdentity windows = new WindowsAppIdentity();
      long window = Pointer.nativeValue(handle.getPointer());
      windows.identify(
          window, Path.of("C:\\Hakamap\\Hakamap.exe"), Path.of("C:\\Hakamap\\app\\hakamap.ico"));
      assertThat(windows.isWindow(window)).isTrue();
      assertThat(windows.ownedProcesses()).contains(ProcessHandle.current().pid());
    } finally {
      user.DestroyWindow(handle);
    }
  }
}
