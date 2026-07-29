package jp.hakamap.infrastructure.fileselection;

public enum FileSelectionMode {
  SINGLE_FILE("singleFile"),
  MULTIPLE_FILES("multipleFiles"),
  DIRECTORY("directory");

  private final String apiValue;

  FileSelectionMode(String apiValue) {
    this.apiValue = apiValue;
  }

  public String apiValue() {
    return apiValue;
  }

  public static FileSelectionMode fromApiValue(String value) {
    return java.util.Arrays.stream(values())
        .filter(mode -> mode.apiValue.equals(value))
        .findFirst()
        .orElseThrow(() -> new FileSelectionException("file-selection-request-invalid"));
  }
}
