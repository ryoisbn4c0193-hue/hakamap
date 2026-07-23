package jp.hakamap.persistence.json.model.catalog;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.time.Instant;
import java.util.UUID;

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "state",
    visible = true)
@JsonSubTypes({
  @JsonSubTypes.Type(value = ActiveCatalogProjectV1.class, name = "active"),
  @JsonSubTypes.Type(value = TrashedCatalogProjectV1.class, name = "trashed")
})
public sealed interface CatalogProjectV1 permits ActiveCatalogProjectV1, TrashedCatalogProjectV1 {
  UUID projectId();

  String path();

  String lastKnownName();

  Instant lastKnownCreatedAt();

  Instant lastKnownUpdatedAt();

  String state();
}
