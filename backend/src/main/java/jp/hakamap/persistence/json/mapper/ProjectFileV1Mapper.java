package jp.hakamap.persistence.json.mapper;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import jp.hakamap.persistence.json.model.project.AreaV1;
import jp.hakamap.persistence.json.model.project.AssetV1;
import jp.hakamap.persistence.json.model.project.AttachmentAssetV1;
import jp.hakamap.persistence.json.model.project.BackgroundAssetV1;
import jp.hakamap.persistence.json.model.project.BackgroundPlacementV1;
import jp.hakamap.persistence.json.model.project.GraveV1;
import jp.hakamap.persistence.json.model.project.PersonV1;
import jp.hakamap.persistence.json.model.project.ProjectFileV1;
import jp.hakamap.persistence.json.model.project.ProjectMetadataV1;
import jp.hakamap.project.domain.model.Area;
import jp.hakamap.project.domain.model.AssetMetadata;
import jp.hakamap.project.domain.model.BackgroundPlacement;
import jp.hakamap.project.domain.model.Grave;
import jp.hakamap.project.domain.model.Person;
import jp.hakamap.project.domain.model.ProjectAggregate;
import jp.hakamap.project.domain.model.ProjectMetadata;
import jp.hakamap.project.domain.value.AreaColorPreset;
import jp.hakamap.project.domain.value.AreaId;
import jp.hakamap.project.domain.value.AreaName;
import jp.hakamap.project.domain.value.AssetDescription;
import jp.hakamap.project.domain.value.AssetDisplayName;
import jp.hakamap.project.domain.value.AssetId;
import jp.hakamap.project.domain.value.AssetType;
import jp.hakamap.project.domain.value.BackgroundScale;
import jp.hakamap.project.domain.value.DisplayOrder;
import jp.hakamap.project.domain.value.GraveId;
import jp.hakamap.project.domain.value.GraveName;
import jp.hakamap.project.domain.value.GraveNotes;
import jp.hakamap.project.domain.value.ManagementNumber;
import jp.hakamap.project.domain.value.MapPoint;
import jp.hakamap.project.domain.value.MapRectangle;
import jp.hakamap.project.domain.value.PersonId;
import jp.hakamap.project.domain.value.PersonName;
import jp.hakamap.project.domain.value.PosthumousName;
import jp.hakamap.project.domain.value.ProjectId;
import jp.hakamap.project.domain.value.ProjectName;
import jp.hakamap.project.domain.value.RotationDegrees;

public final class ProjectFileV1Mapper {
  public ProjectFileV1 toFile(ProjectAggregate aggregate) {
    List<AreaV1> areas =
        aggregate.areas().values().stream()
            .sorted(
                Comparator.comparing(Area::displayOrder).thenComparing(area -> area.id().value()))
            .map(this::toFile)
            .toList();
    List<GraveV1> graves =
        aggregate.graves().values().stream()
            .sorted(Comparator.comparing(grave -> grave.id().value()))
            .map(this::toFile)
            .toList();
    List<PersonV1> people =
        aggregate.people().values().stream()
            .sorted(
                Comparator.comparing((Person person) -> person.graveId().value())
                    .thenComparing(Person::displayOrder)
                    .thenComparing(person -> person.id().value()))
            .map(this::toFile)
            .toList();
    List<AssetV1> assets =
        aggregate.assets().values().stream().sorted(assetComparator()).map(this::toFile).toList();
    return new ProjectFileV1(
        1,
        toFile(aggregate.metadata()),
        aggregate.background().map(this::toFile).orElse(null),
        areas,
        graves,
        people,
        assets);
  }

  public ProjectAggregate toDomain(ProjectFileV1 file) {
    if (file.schemaVersion() != 1) {
      throw new IllegalArgumentException("unsupported-project-schema-version");
    }
    return new ProjectAggregate(
        toDomain(file.project()),
        Optional.ofNullable(file.background()).map(this::toDomain),
        file.areas().stream().map(this::toDomain).toList(),
        file.graves().stream().map(this::toDomain).toList(),
        file.people().stream().map(this::toDomain).toList(),
        file.assets().stream().map(this::toDomain).toList());
  }

  private ProjectMetadataV1 toFile(ProjectMetadata value) {
    return new ProjectMetadataV1(
        value.id().value(), value.name().value(), value.createdAt(), value.updatedAt());
  }

  private ProjectMetadata toDomain(ProjectMetadataV1 value) {
    return new ProjectMetadata(
        new ProjectId(value.id()),
        new ProjectName(value.name()),
        value.createdAt(),
        value.updatedAt());
  }

  private BackgroundPlacementV1 toFile(BackgroundPlacement value) {
    return new BackgroundPlacementV1(
        value.assetId().value(),
        value.position().x(),
        value.position().y(),
        value.rotation().value(),
        value.scaleX().value(),
        value.scaleY().value());
  }

  private BackgroundPlacement toDomain(BackgroundPlacementV1 value) {
    return new BackgroundPlacement(
        new AssetId(value.assetId()),
        new MapPoint(value.x(), value.y()),
        new RotationDegrees(value.rotation()),
        new BackgroundScale(value.scaleX()),
        new BackgroundScale(value.scaleY()));
  }

  private AreaV1 toFile(Area value) {
    return new AreaV1(
        value.id().value(),
        value.name().value(),
        value.rectangle().left(),
        value.rectangle().top(),
        value.rectangle().size().width(),
        value.rectangle().size().height(),
        value.color().name().toLowerCase(Locale.ROOT),
        value.visible(),
        value.displayOrder().value());
  }

  private Area toDomain(AreaV1 value) {
    return new Area(
        new AreaId(value.id()),
        new AreaName(value.name()),
        new MapRectangle(value.x(), value.y(), value.width(), value.height()),
        AreaColorPreset.valueOf(value.colorPreset().toUpperCase(Locale.ROOT)),
        value.visible(),
        new DisplayOrder(value.displayOrder()));
  }

  private GraveV1 toFile(Grave value) {
    return new GraveV1(
        value.id().value(),
        value.managementNumber().map(ManagementNumber::value).orElse(null),
        value.name().map(GraveName::value).orElse(null),
        value.notes().map(GraveNotes::value).orElse(null),
        value.rectangle().left(),
        value.rectangle().top(),
        value.rectangle().size().width(),
        value.rectangle().size().height(),
        value.rotation().value(),
        value.updatedAt());
  }

  private Grave toDomain(GraveV1 value) {
    return new Grave(
        new GraveId(value.id()),
        Optional.ofNullable(value.managementNumber()).map(ManagementNumber::new),
        Optional.ofNullable(value.name()).map(GraveName::new),
        Optional.ofNullable(value.notes()).map(GraveNotes::new),
        new MapRectangle(value.x(), value.y(), value.width(), value.height()),
        new RotationDegrees(value.rotation()),
        value.updatedAt());
  }

  private PersonV1 toFile(Person value) {
    return new PersonV1(
        value.id().value(),
        value.graveId().value(),
        value.name().map(PersonName::value).orElse(null),
        value.posthumousName().map(PosthumousName::value).orElse(null),
        value.createdAt(),
        value.updatedAt(),
        value.displayOrder().value());
  }

  private Person toDomain(PersonV1 value) {
    return new Person(
        new PersonId(value.id()),
        new GraveId(value.graveId()),
        Optional.ofNullable(value.name()).map(PersonName::new),
        Optional.ofNullable(value.posthumousName()).map(PosthumousName::new),
        value.createdAt(),
        value.updatedAt(),
        new DisplayOrder(value.displayOrder()));
  }

  private AssetV1 toFile(AssetMetadata value) {
    if (value.type() == AssetType.BACKGROUND) {
      return new BackgroundAssetV1(
          value.id().value(),
          "background",
          value.originalFileName(),
          value.relativePath(),
          value.sourceMediaType(),
          value.storedMediaType(),
          value.sizeBytes(),
          value.sha256(),
          value.createdAt());
    }
    return new AttachmentAssetV1(
        value.id().value(),
        "attachment",
        value.originalFileName(),
        value.relativePath(),
        value.sourceMediaType(),
        value.storedMediaType(),
        value.sizeBytes(),
        value.sha256(),
        value.createdAt(),
        value.graveId().orElseThrow().value(),
        value.displayName().orElseThrow().value(),
        value.description().map(AssetDescription::value).orElse(null),
        value.updatedAt().orElseThrow(),
        value.displayOrder().orElseThrow().value());
  }

  private AssetMetadata toDomain(AssetV1 value) {
    if (value instanceof BackgroundAssetV1 backgroundAsset) {
      return new AssetMetadata(
          new AssetId(backgroundAsset.id()),
          AssetType.BACKGROUND,
          Optional.empty(),
          backgroundAsset.originalFileName(),
          backgroundAsset.relativePath(),
          backgroundAsset.sourceMediaType(),
          backgroundAsset.storedMediaType(),
          backgroundAsset.sizeBytes(),
          backgroundAsset.sha256(),
          backgroundAsset.createdAt(),
          Optional.empty(),
          Optional.empty(),
          Optional.empty(),
          Optional.empty());
    }
    AttachmentAssetV1 attachment = (AttachmentAssetV1) value;
    return new AssetMetadata(
        new AssetId(attachment.id()),
        AssetType.ATTACHMENT,
        Optional.of(new GraveId(attachment.graveId())),
        attachment.originalFileName(),
        attachment.relativePath(),
        attachment.sourceMediaType(),
        attachment.storedMediaType(),
        attachment.sizeBytes(),
        attachment.sha256(),
        attachment.createdAt(),
        Optional.of(new AssetDisplayName(attachment.displayName())),
        Optional.ofNullable(attachment.description()).map(AssetDescription::new),
        Optional.of(attachment.updatedAt()),
        Optional.of(new DisplayOrder(attachment.displayOrder())));
  }

  private Comparator<AssetMetadata> assetComparator() {
    return Comparator.comparing((AssetMetadata asset) -> asset.type().ordinal())
        .thenComparing(asset -> asset.graveId().map(id -> id.value().toString()).orElse(""))
        .thenComparing(asset -> asset.displayOrder().map(DisplayOrder::value).orElse(-1))
        .thenComparing(asset -> asset.id().value());
  }
}
