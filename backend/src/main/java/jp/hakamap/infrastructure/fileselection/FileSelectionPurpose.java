package jp.hakamap.infrastructure.fileselection;

public enum FileSelectionPurpose {
  BACKGROUND_IMPORT("backgroundImport", FileSelectionMode.SINGLE_FILE),
  ATTACHMENT_IMPORT("attachmentImport", FileSelectionMode.MULTIPLE_FILES),
  PROJECT_CREATE_DIRECTORY("projectCreateDirectory", FileSelectionMode.DIRECTORY),
  PROJECT_RELINK_DIRECTORY("projectRelinkDirectory", FileSelectionMode.DIRECTORY),
  PROJECT_SAVE_AS_DIRECTORY("projectSaveAsDirectory", FileSelectionMode.DIRECTORY),
  EXPORT_DESTINATION("exportDestination", FileSelectionMode.SINGLE_FILE),
  IMPORT_ARCHIVE("importArchive", FileSelectionMode.SINGLE_FILE),
  IMPORT_DESTINATION_DIRECTORY("importDestinationDirectory", FileSelectionMode.DIRECTORY),
  TRASH_RESTORE_DIRECTORY("trashRestoreDirectory", FileSelectionMode.DIRECTORY);

  private final String apiValue;

  private final FileSelectionMode requiredMode;

  FileSelectionPurpose(String apiValue, FileSelectionMode requiredMode) {
    this.apiValue = apiValue;
    this.requiredMode = requiredMode;
  }

  public String apiValue() {
    return apiValue;
  }

  public FileSelectionMode requiredMode() {
    return requiredMode;
  }

  public static FileSelectionPurpose fromApiValue(String value) {
    return java.util.Arrays.stream(values())
        .filter(purpose -> purpose.apiValue.equals(value))
        .findFirst()
        .orElseThrow(() -> new FileSelectionException("file-selection-request-invalid"));
  }
}
