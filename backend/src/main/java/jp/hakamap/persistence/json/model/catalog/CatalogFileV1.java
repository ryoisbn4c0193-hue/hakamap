package jp.hakamap.persistence.json.model.catalog;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"schemaVersion", "defaultProjectId", "projects"})
public record CatalogFileV1(
    int schemaVersion, UUID defaultProjectId, List<CatalogProjectV1> projects) {
  public CatalogFileV1 {
    projects = projects == null ? null : List.copyOf(projects);
  }
}
