package jp.hakamap.infrastructure.fileselection;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import jp.hakamap.infrastructure.http.LocalApiSecurityFilter;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/file-selections")
public class FileSelectionController {
  private final FileSelectionService selections;

  public FileSelectionController(FileSelectionService selections) {
    this.selections = selections;
  }

  @PostMapping
  FileSelectionService.FileSelectionResult start(
      @RequestBody FileSelectionRequest body, HttpServletRequest request) {
    try {
      return selections.start(
          sessionId(request),
          FileSelectionMode.fromApiValue(body.selectionMode()),
          FileSelectionPurpose.fromApiValue(body.purpose()));
    } catch (IllegalArgumentException exception) {
      throw new FileSelectionException("file-selection-request-invalid");
    }
  }

  @DeleteMapping("/{fileSelectionId}")
  ResponseEntity<Void> invalidate(@PathVariable UUID fileSelectionId, HttpServletRequest request) {
    selections.invalidate(fileSelectionId, sessionId(request));
    return ResponseEntity.noContent().build();
  }

  private String sessionId(HttpServletRequest request) {
    return (String) request.getAttribute(LocalApiSecurityFilter.AUTHENTICATED_SESSION_ATTRIBUTE);
  }

  public record FileSelectionRequest(String selectionMode, String purpose) {}
}
