package jp.hakamap.persistence.json.model.recovery;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.UUID;

@JsonPropertyOrder({"assetId", "tempRelativePath", "sizeBytes", "sha256"})
public record StagedAssetV1(UUID assetId, String tempRelativePath, long sizeBytes, String sha256) {}
