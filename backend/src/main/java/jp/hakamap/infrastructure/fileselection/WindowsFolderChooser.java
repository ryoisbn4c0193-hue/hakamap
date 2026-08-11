package jp.hakamap.infrastructure.fileselection;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.COM.COMInvoker;
import com.sun.jna.platform.win32.Guid.CLSID;
import com.sun.jna.platform.win32.Guid.IID;
import com.sun.jna.platform.win32.Ole32;
import com.sun.jna.platform.win32.WTypes;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinNT.HRESULT;
import com.sun.jna.ptr.PointerByReference;
import java.awt.Component;
import java.nio.file.Path;
import java.util.List;

/** Windows標準のIFileDialogを使用してフォルダーを選択する。 */
final class WindowsFolderChooser {
  private static final CLSID FILE_OPEN_DIALOG = new CLSID("{DC1C5A9C-E88A-4DDE-A5A1-60F82A20AEF7}");

  private static final IID I_FILE_OPEN_DIALOG = new IID("{D57C7288-D4AD-4768-BE02-9D969532D960}");

  private static final int CLSCTX_INPROC_SERVER = 0x1;

  private static final int FOS_PICK_FOLDERS = 0x20;

  private static final int FOS_FORCE_FILE_SYSTEM = 0x40;

  private static final int FOS_NO_CHANGE_DIR = 0x8;

  private static final int FOS_PATH_MUST_EXIST = 0x800;

  private static final int SIGDN_FILE_SYSTEM_PATH = 0x80058000;

  private static final int ERROR_CANCELLED = 0x800704C7;

  List<Path> choose(Component owner) {
    HRESULT initialized = Ole32.INSTANCE.CoInitializeEx(null, Ole32.COINIT_APARTMENTTHREADED);
    if (initialized.intValue() < 0) {
      throw new FileSelectionException("file-selection-failed");
    }
    try {
      return showDialog(owner);
    } finally {
      Ole32.INSTANCE.CoUninitialize();
    }
  }

  private List<Path> showDialog(Component owner) {
    PointerByReference reference = new PointerByReference();
    HRESULT created =
        Ole32.INSTANCE.CoCreateInstance(
            FILE_OPEN_DIALOG, null, CLSCTX_INPROC_SERVER, I_FILE_OPEN_DIALOG, reference);
    requireSuccess(created);
    FileOpenDialog dialog = new FileOpenDialog(reference.getValue());
    try {
      requireSuccess(
          dialog.setOptions(
              FOS_PICK_FOLDERS | FOS_FORCE_FILE_SYSTEM | FOS_NO_CHANGE_DIR | FOS_PATH_MUST_EXIST));
      requireSuccess(dialog.setTitle("保存先フォルダーを選択"));
      HWND ownerHandle = new HWND(Native.getComponentPointer(owner));
      HRESULT shown = dialog.show(ownerHandle);
      if (shown.intValue() == ERROR_CANCELLED) {
        return List.of();
      }
      requireSuccess(shown);
      PointerByReference itemReference = new PointerByReference();
      requireSuccess(dialog.getResult(itemReference));
      ShellItem item = new ShellItem(itemReference.getValue());
      try {
        PointerByReference pathReference = new PointerByReference();
        requireSuccess(item.getDisplayName(SIGDN_FILE_SYSTEM_PATH, pathReference));
        Pointer pathPointer = pathReference.getValue();
        try {
          return List.of(Path.of(pathPointer.getWideString(0)));
        } finally {
          Ole32.INSTANCE.CoTaskMemFree(pathPointer);
        }
      } finally {
        item.release();
      }
    } finally {
      dialog.release();
    }
  }

  private void requireSuccess(HRESULT result) {
    if (result.intValue() < 0) {
      throw new FileSelectionException("file-selection-failed");
    }
  }

  private static final class FileOpenDialog extends COMInvoker {
    private FileOpenDialog(Pointer pointer) {
      setPointer(pointer);
    }

    private HRESULT show(HWND owner) {
      return invoke(3, owner);
    }

    private HRESULT setOptions(int options) {
      return invoke(9, options);
    }

    private HRESULT setTitle(String title) {
      return invoke(17, new WTypes.LPOLESTR(title));
    }

    private HRESULT getResult(PointerByReference result) {
      return invoke(20, result);
    }

    private void release() {
      _invokeNativeInt(2, new Object[] {getPointer()});
    }

    private HRESULT invoke(int index, Object... arguments) {
      Object[] parameters = new Object[arguments.length + 1];
      parameters[0] = getPointer();
      System.arraycopy(arguments, 0, parameters, 1, arguments.length);
      return (HRESULT) _invokeNativeObject(index, parameters, HRESULT.class);
    }
  }

  private static final class ShellItem extends COMInvoker {
    private ShellItem(Pointer pointer) {
      setPointer(pointer);
    }

    private HRESULT getDisplayName(int displayName, PointerByReference result) {
      return (HRESULT)
          _invokeNativeObject(5, new Object[] {getPointer(), displayName, result}, HRESULT.class);
    }

    private void release() {
      _invokeNativeInt(2, new Object[] {getPointer()});
    }
  }
}
