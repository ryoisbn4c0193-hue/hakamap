package jp.hakamap.infrastructure.http;

import jp.hakamap.infrastructure.fileselection.FileSelectionException;
import jp.hakamap.project.application.catalog.ProjectCatalogException;
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
