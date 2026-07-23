package jp.hakamap.project.domain.value;

public final class DomainValidationException extends IllegalArgumentException {
  private final String code;

  public DomainValidationException(String code) {
    super(code);
    this.code = code;
  }

  public String code() {
    return code;
  }
}
