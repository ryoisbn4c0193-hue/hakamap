package jp.hakamap.project.domain.model;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;
import jp.hakamap.project.domain.value.GraveId;
import jp.hakamap.project.domain.value.GraveName;
import jp.hakamap.project.domain.value.GraveNotes;
import jp.hakamap.project.domain.value.ManagementNumber;
import jp.hakamap.project.domain.value.MapRectangle;
import jp.hakamap.project.domain.value.RotationDegrees;

public record Grave(
    GraveId id,
    Optional<ManagementNumber> managementNumber,
    Optional<GraveName> name,
    Optional<GraveNotes> notes,
    MapRectangle rectangle,
    RotationDegrees rotation,
    Instant updatedAt) {
  public Grave {
    Objects.requireNonNull(id, "id");
    managementNumber = ListCopies.optional(managementNumber);
    name = ListCopies.optional(name);
    notes = ListCopies.optional(notes);
    Objects.requireNonNull(rectangle, "rectangle");
    Objects.requireNonNull(rotation, "rotation");
    updatedAt = Objects.requireNonNull(updatedAt, "updatedAt").truncatedTo(ChronoUnit.MILLIS);
  }

  public Grave move(MapRectangle target, Instant commandTime) {
    return new Grave(id, managementNumber, name, notes, target, rotation, commandTime);
  }

  public Grave number(ManagementNumber number, Instant commandTime) {
    return new Grave(id, Optional.of(number), name, notes, rectangle, rotation, commandTime);
  }
}
