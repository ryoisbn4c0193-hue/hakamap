package jp.hakamap.infrastructure.fileselection;

public final class FileSelectionException extends RuntimeException {
  private final String code;

  public FileSelectionException(String code) {
    super(code);
    this.code = code;
  }

  public String code() {
    return code;
  }
}
