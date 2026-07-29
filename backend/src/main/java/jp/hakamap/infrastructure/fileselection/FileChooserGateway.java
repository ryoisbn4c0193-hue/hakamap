package jp.hakamap.infrastructure.fileselection;

import java.nio.file.Path;
import java.util.List;

@FunctionalInterface
public interface FileChooserGateway {
  List<Path> choose(FileSelectionMode mode);
}
