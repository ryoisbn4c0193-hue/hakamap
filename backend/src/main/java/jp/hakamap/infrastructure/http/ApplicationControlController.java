package jp.hakamap.infrastructure.http;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import jp.hakamap.infrastructure.lifecycle.ApplicationShutdownRequester;
import jp.hakamap.infrastructure.lifecycle.RuntimeLifecycleService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
public class ApplicationControlController {
  private final LocalRequestValidator requests;

  private final Optional<RuntimeLifecycleService> runtime;

  private final ApplicationShutdownRequester shutdown;

  public ApplicationControlController(
      LocalRequestValidator requests,
      ObjectProvider<RuntimeLifecycleService> runtime,
      ApplicationShutdownRequester shutdown) {
    this.requests = requests;
    this.runtime = runtime.stream().findFirst();
    this.shutdown = shutdown;
  }

  @PostMapping("/api/internal/reopen")
  ResponseEntity<Void> reopen(
      HttpServletRequest request,
      @RequestHeader(value = "X-Hakamap-Instance-Id", required = false) String instanceId,
      @RequestHeader(value = "X-Hakamap-Control-Token", required = false) String controlToken) {
    if (!requests.isLoopback(request) || !requests.hasValidHost(request)) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN)
          .cacheControl(CacheControl.noStore())
          .build();
    }
    RuntimeLifecycleService service = runtime.orElse(null);
    if (service == null || !service.authenticateControl(instanceId, controlToken)) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
          .cacheControl(CacheControl.noStore())
          .build();
    }
    service.openBrowser();
    return ResponseEntity.noContent()
        .header("X-Hakamap-Instance-Id", service.instanceId().toString())
        .cacheControl(CacheControl.noStore())
        .build();
  }

  @PostMapping("/api/v1/application/exit")
  ResponseEntity<Void> exit() {
    shutdown.requestShutdown();
    return ResponseEntity.accepted().cacheControl(CacheControl.noStore()).build();
  }
}
