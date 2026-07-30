package jp.hakamap.infrastructure.fileselection;

import java.nio.file.Path;
import java.util.List;

@FunctionalInterface
public interface FileChooserGateway {
  List<Path> choose(FileSelectionMode mode);

  default List<Path> choose(FileSelectionMode mode, FileSelectionPurpose purpose) {
    return choose(mode);
  }
}
