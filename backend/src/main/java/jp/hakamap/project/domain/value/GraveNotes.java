package jp.hakamap.project.domain.value;

public record GraveNotes(String value) {
  public GraveNotes(String value) {
    this.value =
        TextValues.optionalMultiLine(value, 1000, "invalid-grave-notes")
            .orElseThrow(() -> new DomainValidationException("invalid-grave-notes"));
  }
}
