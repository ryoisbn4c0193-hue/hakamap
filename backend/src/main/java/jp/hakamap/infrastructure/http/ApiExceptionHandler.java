package jp.hakamap.infrastructure.http;

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

  @ExceptionHandler(Exception.class)
  ResponseEntity<ProblemDetail> unexpected(Exception exception) {
    LOGGER.error("internal-error");
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .header("Cache-Control", "no-store")
        .body(HakamapProblem.create(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error"));
  }
}
