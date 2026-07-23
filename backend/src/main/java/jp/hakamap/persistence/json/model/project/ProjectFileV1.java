package jp.hakamap.persistence.json.model.project;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
  "schemaVersion",
  "project",
  "background",
  "areas",
  "graves",
  "people",
  "assets"
})
public record ProjectFileV1(
    int schemaVersion,
    ProjectMetadataV1 project,
    BackgroundPlacementV1 background,
    List<AreaV1> areas,
    List<GraveV1> graves,
    List<PersonV1> people,
    List<AssetV1> assets) {
  public ProjectFileV1 {
    areas = areas == null ? null : List.copyOf(areas);
    graves = graves == null ? null : List.copyOf(graves);
    people = people == null ? null : List.copyOf(people);
    assets = assets == null ? null : List.copyOf(assets);
  }
}
