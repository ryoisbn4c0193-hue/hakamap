package jp.hakamap.infrastructure.fileselection;

import java.awt.FileDialog;
import java.awt.GraphicsEnvironment;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

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
    if (mode != FileSelectionMode.DIRECTORY && !SwingUtilities.isEventDispatchThread()) {
      return chooseNativeFiles(mode, purpose);
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
                    ? chooseDirectory(owner)
                    : showNativeFileDialog(owner, mode, purpose));
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

  private List<Path> chooseNativeFiles(FileSelectionMode mode, FileSelectionPurpose purpose) {
    AtomicReference<JFrame> owner = new AtomicReference<>();
    AtomicReference<List<Path>> result = new AtomicReference<>(List.of());
    CountDownLatch focused = new CountDownLatch(1);
    invokeAndWait(
        () -> {
          JFrame frame = createOwner();
          frame.addWindowFocusListener(
              new java.awt.event.WindowAdapter() {
                @Override
                public void windowGainedFocus(java.awt.event.WindowEvent event) {
                  focused.countDown();
                }
              });
          owner.set(frame);
          frame.setVisible(true);
          frame.toFront();
          frame.requestFocus();
        });
    try {
      focused.await(1, TimeUnit.SECONDS);
      invokeAndWait(
          () -> {
            JFrame frame = owner.get();
            frame.toFront();
            frame.requestFocus();
            result.set(showNativeFileDialog(frame, mode, purpose));
          });
      return result.get();
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new FileSelectionException("file-selection-failed");
    } finally {
      invokeAndWait(() -> owner.get().dispose());
    }
  }

  private List<Path> showNativeFileDialog(
      JFrame owner, FileSelectionMode mode, FileSelectionPurpose purpose) {
    int dialogMode =
        purpose == FileSelectionPurpose.EXPORT_DESTINATION ? FileDialog.SAVE : FileDialog.LOAD;
    FileDialog chooser = new FileDialog(owner, "ファイルを選択", dialogMode);
    chooser.setAlwaysOnTop(true);
    chooser.setMultipleMode(mode == FileSelectionMode.MULTIPLE_FILES);
    chooser.setLocationRelativeTo(null);
    chooser.addWindowListener(
        new WindowAdapter() {
          @Override
          public void windowOpened(WindowEvent event) {
            chooser.toFront();
            chooser.requestFocus();
            Timer foregroundRetry =
                new Timer(
                    200,
                    ignored -> {
                      chooser.setAlwaysOnTop(true);
                      chooser.toFront();
                      chooser.requestFocus();
                    });
            foregroundRetry.setRepeats(false);
            foregroundRetry.start();
          }
        });
    chooser.setVisible(true);
    File[] selected = chooser.getFiles();
    chooser.dispose();
    return Arrays.stream(selected).map(File::toPath).toList();
  }

  private JFrame createOwner() {
    JFrame owner = new JFrame();
    owner.setAlwaysOnTop(true);
    owner.setType(java.awt.Window.Type.UTILITY);
    owner.setUndecorated(true);
    owner.setSize(1, 1);
    owner.setLocationRelativeTo(null);
    return owner;
  }

  private void invokeAndWait(Runnable action) {
    try {
      SwingUtilities.invokeAndWait(action);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new FileSelectionException("file-selection-failed");
    } catch (InvocationTargetException exception) {
      throw new FileSelectionException("file-selection-failed");
    }
  }

  private List<Path> chooseDirectory(JFrame owner) {
    if (System.getProperty("os.name", "").startsWith("Windows")) {
      return new WindowsFolderChooser().choose(owner);
    }
    throw new FileSelectionException("file-selection-unavailable");
  }
}
