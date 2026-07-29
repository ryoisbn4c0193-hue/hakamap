package jp.hakamap.infrastructure.http;

import jp.hakamap.infrastructure.fileselection.FileSelectionException;
import jp.hakamap.project.application.catalog.ProjectCatalogException;
import jp.hakamap.project.application.editing.EditingApiException;
import jp.hakamap.project.application.history.EditingSessionException;
import jp.hakamap.project.domain.result.ProjectInvariantException;
import jp.hakamap.project.domain.value.DomainValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
  private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

  @ExceptionHandler(FileSelectionException.class)
  ResponseEntity<ProblemDetail> fileSelection(FileSelectionException exception) {
    HttpStatus status =
        switch (exception.code()) {
          case "file-selection-request-invalid" -> HttpStatus.BAD_REQUEST;
          case "file-selection-dialog-already-open" -> HttpStatus.CONFLICT;
          case "file-selection-not-found" -> HttpStatus.NOT_FOUND;
          case "file-selection-unavailable", "file-selection-invalid" ->
              HttpStatus.UNPROCESSABLE_ENTITY;
          default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    return problem(status, exception.code());
  }

  @ExceptionHandler(ProjectCatalogException.class)
  ResponseEntity<ProblemDetail> projectCatalog(ProjectCatalogException exception) {
    HttpStatus status =
        switch (exception.code()) {
          case "catalog-project-not-found", "project-not-open" -> HttpStatus.NOT_FOUND;
          case "project-busy",
              "catalog-project-duplicate",
              "project-destination-exists",
              "project-trash-destination-exists",
              "project-restore-destination-exists" ->
              HttpStatus.CONFLICT;
          case "catalog-default-invalid",
              "project-mismatch",
              "project-close-action-invalid",
              "storage-atomic-move-unsupported" ->
              HttpStatus.UNPROCESSABLE_ENTITY;
          default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    return problem(status, exception.code());
  }

  @ExceptionHandler(EditingApiException.class)
  ResponseEntity<ProblemDetail> editingApi(EditingApiException exception) {
    HttpStatus status =
        switch (exception.code()) {
          case "request-unknown-command", "request-field-invalid" -> HttpStatus.BAD_REQUEST;
          case "asset-not-found" -> HttpStatus.NOT_FOUND;
          case "command-confirmation-invalid", "numbering-preview-invalid" -> HttpStatus.CONFLICT;
          case "asset-selection-invalid" -> HttpStatus.FORBIDDEN;
          default -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
    return problem(status, exception.code());
  }

  @ExceptionHandler(EditingSessionException.class)
  ResponseEntity<ProblemDetail> editingSession(EditingSessionException exception) {
    HttpStatus status =
        switch (exception.code()) {
          case "project-revision-conflict", "undo-empty", "redo-empty" -> HttpStatus.CONFLICT;
          case "editing-stopped" -> HttpStatus.LOCKED;
          default -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
    String code =
        switch (exception.code()) {
          case "undo-empty" -> "history-undo-empty";
          case "redo-empty" -> "history-redo-empty";
          case "editing-stopped" -> "storage-project-locked";
          default -> exception.code();
        };
    return problem(status, code);
  }

  @ExceptionHandler(ProjectInvariantException.class)
  ResponseEntity<ProblemDetail> projectInvariant(ProjectInvariantException exception) {
    HttpStatus status =
        exception.code().endsWith("-not-found")
            ? HttpStatus.NOT_FOUND
            : HttpStatus.UNPROCESSABLE_ENTITY;
    return problem(status, exception.code());
  }

  @ExceptionHandler({DomainValidationException.class, IllegalArgumentException.class})
  ResponseEntity<ProblemDetail> invalidRequest(RuntimeException exception) {
    return problem(HttpStatus.BAD_REQUEST, "request-invalid");
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ProblemDetail> unexpected(Exception exception) {
    LOGGER.error("internal-error");
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .header("Cache-Control", "no-store")
        .body(HakamapProblem.create(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error"));
  }

  private ResponseEntity<ProblemDetail> problem(HttpStatus status, String code) {
    return ResponseEntity.status(status)
        .header("Cache-Control", "no-store")
        .body(HakamapProblem.create(status, code));
  }
}
