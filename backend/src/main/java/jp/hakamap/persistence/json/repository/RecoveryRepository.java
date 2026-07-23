package jp.hakamap.persistence.json.repository;

import java.nio.file.Path;
import jp.hakamap.persistence.json.model.recovery.RecoveryFileV1;

public interface RecoveryRepository {
  RecoveryFileV1 read(Path recoveryFile);

  void write(Path recoveryFile, RecoveryFileV1 recovery);
}
