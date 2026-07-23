package jp.hakamap.project.domain.result;

public final class ProjectInvariantException extends IllegalArgumentException {
  private final String code;

  public ProjectInvariantException(String code) {
    super(code);
    this.code = code;
  }

  public String code() {
    return code;
  }
}
