package jp.hakamap.infrastructure.lifecycle;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.time.Duration;
import tools.jackson.databind.json.JsonMapper;

final class ExistingInstanceClient {
  private static final Duration RETRY_LIMIT = Duration.ofSeconds(10);

  private final JsonMapper jsonMapper;

  private final HttpClient httpClient;

  ExistingInstanceClient(JsonMapper jsonMapper) {
    this.jsonMapper = jsonMapper;
    this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
  }

  boolean requestReopen(RuntimePaths paths) {
    long deadline = System.nanoTime() + RETRY_LIMIT.toNanos();
    while (System.nanoTime() < deadline) {
      RuntimeInstance instance = readInstance(paths);
      if (instance != null && ProcessHandle.of(instance.processId()).isPresent()) {
        if (sendReopen(instance)) {
          return true;
        }
      }
      try {
        Thread.sleep(200);
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        return false;
      }
    }
    return false;
  }

  private RuntimeInstance readInstance(RuntimePaths paths) {
    try {
      return jsonMapper.readValue(Files.readAllBytes(paths.instanceFile()), RuntimeInstance.class);
    } catch (IOException | RuntimeException exception) {
      return null;
    }
  }

  private boolean sendReopen(RuntimeInstance instance) {
    try {
      URI endpoint = URI.create("http://127.0.0.1:" + instance.port() + "/api/internal/reopen");
      HttpRequest request =
          HttpRequest.newBuilder(endpoint)
              .timeout(Duration.ofSeconds(1))
              .header("X-Hakamap-Instance-Id", instance.instanceId().toString())
              .header("X-Hakamap-Control-Token", instance.controlToken())
              .POST(HttpRequest.BodyPublishers.noBody())
              .build();
      HttpResponse<Void> response =
          httpClient.send(request, HttpResponse.BodyHandlers.discarding());
      return response.statusCode() == 204
          && response
              .headers()
              .firstValue("X-Hakamap-Instance-Id")
              .filter(instance.instanceId().toString()::equals)
              .isPresent();
    } catch (IOException | InterruptedException | IllegalArgumentException exception) {
      if (exception instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      return false;
    }
  }
}
