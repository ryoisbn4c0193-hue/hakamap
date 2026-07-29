package jp.hakamap.project.application.editing;

public final class EditingApiException extends RuntimeException {
  private final String code;

  public EditingApiException(String code) {
    super(code);
    this.code = code;
  }

  public EditingApiException(String code, Throwable cause) {
    super(code, cause);
    this.code = code;
  }

  public String code() {
    return code;
  }
}
