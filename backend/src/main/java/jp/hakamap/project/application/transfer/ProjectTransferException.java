package jp.hakamap.project.application.transfer;

public final class ProjectTransferException extends RuntimeException {
  public ProjectTransferException(String code) {
    super(code);
  }

  public ProjectTransferException(String code, Throwable cause) {
    super(code, cause);
  }
}
