package jp.hakamap.project.application.history;

public final class EditingSessionException extends RuntimeException {
  private final String code;

  public EditingSessionException(String code) {
    super(code);
    this.code = code;
  }

  public String code() {
    return code;
  }
}
