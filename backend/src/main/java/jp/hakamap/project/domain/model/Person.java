package jp.hakamap.project.domain.model;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;
import jp.hakamap.project.domain.value.DisplayOrder;
import jp.hakamap.project.domain.value.GraveId;
import jp.hakamap.project.domain.value.PersonId;
import jp.hakamap.project.domain.value.PersonName;
import jp.hakamap.project.domain.value.PosthumousName;

public record Person(
    PersonId id,
    GraveId graveId,
    Optional<PersonName> name,
    Optional<PosthumousName> posthumousName,
    Instant createdAt,
    Instant updatedAt,
    DisplayOrder displayOrder) {
  public Person {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(graveId, "graveId");
    name = ListCopies.optional(name);
    posthumousName = ListCopies.optional(posthumousName);
    if (name.isEmpty() && posthumousName.isEmpty()) {
      throw new IllegalArgumentException("person-name-required");
    }
    createdAt = Objects.requireNonNull(createdAt, "createdAt").truncatedTo(ChronoUnit.MILLIS);
    updatedAt = Objects.requireNonNull(updatedAt, "updatedAt").truncatedTo(ChronoUnit.MILLIS);
    Objects.requireNonNull(displayOrder, "displayOrder");
    if (updatedAt.isBefore(createdAt)) {
      throw new IllegalArgumentException("person-updated-before-created");
    }
  }

  public Person withDisplayOrder(DisplayOrder order) {
    return new Person(id, graveId, name, posthumousName, createdAt, updatedAt, order);
  }
}
