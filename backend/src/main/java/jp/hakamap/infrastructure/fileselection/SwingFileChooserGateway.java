package jp.hakamap.infrastructure.fileselection;

import java.awt.FileDialog;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

public final class SwingFileChooserGateway implements FileChooserGateway {
  @Override
  public List<Path> choose(FileSelectionMode mode) {
    return choose(mode, null);
  }

  @Override
  public List<Path> choose(FileSelectionMode mode, FileSelectionPurpose purpose) {
    if (GraphicsEnvironment.isHeadless()) {
      throw new FileSelectionException("file-selection-unavailable");
    }
    AtomicReference<List<Path>> result = new AtomicReference<>(List.of());
    Runnable dialog =
        () -> {
          JFrame owner = new JFrame();
          owner.setAlwaysOnTop(true);
          owner.setType(java.awt.Window.Type.UTILITY);
          owner.setUndecorated(true);
          owner.setSize(1, 1);
          owner.setLocationRelativeTo(null);
          owner.setVisible(true);
          owner.toFront();
          owner.requestFocus();
          try {
            result.set(
                mode == FileSelectionMode.DIRECTORY
                    ? chooseDirectory(owner, purpose)
                    : chooseNativeFiles(owner, mode, purpose));
          } finally {
            owner.dispose();
          }
        };
    try {
      if (SwingUtilities.isEventDispatchThread()) {
        dialog.run();
      } else {
        SwingUtilities.invokeAndWait(dialog);
      }
      return result.get();
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new FileSelectionException("file-selection-failed");
    } catch (InvocationTargetException exception) {
      throw new FileSelectionException("file-selection-failed");
    }
  }

  private List<Path> chooseNativeFiles(
      JFrame owner, FileSelectionMode mode, FileSelectionPurpose purpose) {
    int dialogMode =
        purpose == FileSelectionPurpose.EXPORT_DESTINATION ? FileDialog.SAVE : FileDialog.LOAD;
    FileDialog chooser = new FileDialog(owner, "ファイルを選択", dialogMode);
    chooser.setAlwaysOnTop(true);
    chooser.setMultipleMode(mode == FileSelectionMode.MULTIPLE_FILES);
    chooser.setLocationRelativeTo(null);
    chooser.setVisible(true);
    File[] selected = chooser.getFiles();
    chooser.dispose();
    return Arrays.stream(selected).map(File::toPath).toList();
  }

  private List<Path> chooseDirectory(JFrame owner, FileSelectionPurpose purpose) {
    JFileChooser chooser = new JFileChooser();
    chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
    int answer =
        purpose == FileSelectionPurpose.EXPORT_DESTINATION
            ? chooser.showSaveDialog(owner)
            : chooser.showOpenDialog(owner);
    return answer == JFileChooser.APPROVE_OPTION
        ? List.of(chooser.getSelectedFile().toPath())
        : List.of();
  }
}
