package jp.hakamap.persistence.json;

public final class JsonPersistenceException extends RuntimeException {
  private final String code;

  public JsonPersistenceException(String code) {
    super(code);
    this.code = code;
  }

  public JsonPersistenceException(String code, Throwable cause) {
    super(code, cause);
    this.code = code;
  }

  public String code() {
    return code;
  }
}
