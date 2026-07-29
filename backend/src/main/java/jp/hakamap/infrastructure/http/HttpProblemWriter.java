package jp.hakamap.infrastructure.http;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.json.JsonMapper;

public final class HttpProblemWriter {
  private final JsonMapper jsonMapper;

  public HttpProblemWriter(JsonMapper jsonMapper) {
    this.jsonMapper = jsonMapper;
  }

  public void write(HttpServletResponse response, HttpStatus status, String code)
      throws IOException {
    response.setStatus(status.value());
    response.setContentType("application/problem+json");
    response.setCharacterEncoding(java.nio.charset.StandardCharsets.UTF_8.name());
    response.setHeader("Cache-Control", "no-store");
    jsonMapper.writeValue(response.getOutputStream(), HakamapProblem.create(status, code));
  }
}
