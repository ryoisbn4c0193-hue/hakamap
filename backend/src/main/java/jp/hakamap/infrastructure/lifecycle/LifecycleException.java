package jp.hakamap.infrastructure.lifecycle;

public final class LifecycleException extends RuntimeException {
  private final String code;

  public LifecycleException(String code) {
    super(code);
    this.code = code;
  }

  public LifecycleException(String code, Throwable cause) {
    super(code, cause);
    this.code = code;
  }

  public String code() {
    return code;
  }
}
