package jp.hakamap.persistence.json.model.project;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.UUID;

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "assetType",
    visible = true)
@JsonSubTypes({
  @JsonSubTypes.Type(value = BackgroundAssetV1.class, name = "background"),
  @JsonSubTypes.Type(value = AttachmentAssetV1.class, name = "attachment")
})
public sealed interface AssetV1 permits BackgroundAssetV1, AttachmentAssetV1 {
  UUID id();

  String assetType();
}
