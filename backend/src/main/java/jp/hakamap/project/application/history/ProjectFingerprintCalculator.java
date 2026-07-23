package jp.hakamap.project.application.history;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import jp.hakamap.persistence.json.DefensiveJsonCodec;
import jp.hakamap.persistence.json.JsonDocumentType;
import jp.hakamap.persistence.json.mapper.ProjectFileV1Mapper;
import jp.hakamap.project.domain.model.ProjectAggregate;

public final class ProjectFingerprintCalculator {
  private final DefensiveJsonCodec codec;

  private final ProjectFileV1Mapper mapper;

  public ProjectFingerprintCalculator(DefensiveJsonCodec codec, ProjectFileV1Mapper mapper) {
    this.codec = codec;
    this.mapper = mapper;
  }

  public StateFingerprint calculate(ProjectAggregate project) {
    byte[] bytes = codec.write(mapper.toFile(project), JsonDocumentType.PROJECT);
    try {
      return new StateFingerprint(
          HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256を利用できません。", exception);
    }
  }
}
