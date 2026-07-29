package jp.hakamap.infrastructure.fileselection;

import java.awt.GraphicsEnvironment;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;

public final class SwingFileChooserGateway implements FileChooserGateway {
  @Override
  public List<Path> choose(FileSelectionMode mode) {
    if (GraphicsEnvironment.isHeadless()) {
      throw new FileSelectionException("file-selection-unavailable");
    }
    AtomicReference<List<Path>> result = new AtomicReference<>(List.of());
    Runnable dialog =
        () -> {
          JFileChooser chooser = new JFileChooser();
          chooser.setFileSelectionMode(
              mode == FileSelectionMode.DIRECTORY
                  ? JFileChooser.DIRECTORIES_ONLY
                  : JFileChooser.FILES_ONLY);
          chooser.setMultiSelectionEnabled(mode == FileSelectionMode.MULTIPLE_FILES);
          if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) {
            return;
          }
          File[] selected =
              mode == FileSelectionMode.MULTIPLE_FILES
                  ? chooser.getSelectedFiles()
                  : new File[] {chooser.getSelectedFile()};
          result.set(Arrays.stream(selected).map(File::toPath).toList());
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
}
