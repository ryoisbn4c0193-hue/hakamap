package jp.hakamap.persistence.json.model.recovery;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import jp.hakamap.persistence.json.model.project.ProjectFileV1;

@JsonPropertyOrder({
  "recoverySchemaVersion",
  "applicationVersion",
  "projectId",
  "createdAt",
  "baseProjectSha256",
  "projectSnapshot",
  "stagedAssets"
})
public record RecoveryFileV1(
    int recoverySchemaVersion,
    String applicationVersion,
    UUID projectId,
    @JsonFormat(pattern = "uuuu-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC") Instant createdAt,
    String baseProjectSha256,
    ProjectFileV1 projectSnapshot,
    List<StagedAssetV1> stagedAssets) {
  public RecoveryFileV1 {
    stagedAssets = stagedAssets == null ? null : List.copyOf(stagedAssets);
  }
}
