package jp.hakamap.infrastructure.fileselection;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.DWORD;
import com.sun.jna.platform.win32.WinDef.HWND;
import java.awt.Window;

/** ブラウザとは別プロセスのダイアログ所有ウィンドウを前面化する。 */
final class WindowsDialogForeground {
  private WindowsDialogForeground() {}

  static void activate(Window owner) {
    if (!System.getProperty("os.name", "").startsWith("Windows")) {
      return;
    }
    User32 windows = User32.INSTANCE;
    HWND handle = new HWND(Native.getComponentPointer(owner));
    if (windows.SetForegroundWindow(handle)) {
      return;
    }
    HWND foreground = windows.GetForegroundWindow();
    if (foreground == null) {
      return;
    }
    int ownerThread = windows.GetWindowThreadProcessId(handle, null);
    int foregroundThread = windows.GetWindowThreadProcessId(foreground, null);
    if (ownerThread == 0 || foregroundThread == 0 || ownerThread == foregroundThread) {
      return;
    }
    DWORD ownerThreadId = new DWORD(Integer.toUnsignedLong(ownerThread));
    DWORD foregroundThreadId = new DWORD(Integer.toUnsignedLong(foregroundThread));
    // 初回のAWTウィンドウが前面化を拒否された場合だけ、入力キューを一時的に共有する。
    if (!windows.AttachThreadInput(ownerThreadId, foregroundThreadId, true)) {
      return;
    }
    try {
      windows.BringWindowToTop(handle);
      windows.SetForegroundWindow(handle);
    } finally {
      // ファイル選択中には共有せず、キャンセルや例外にかかわらず解除する。
      windows.AttachThreadInput(ownerThreadId, foregroundThreadId, false);
    }
  }
}
