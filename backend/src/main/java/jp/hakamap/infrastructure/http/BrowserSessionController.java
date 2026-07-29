package jp.hakamap.infrastructure.http;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
public class BrowserSessionController {
  private final BrowserSessionRegistry sessions;

  private final LocalRequestValidator requests;

  public BrowserSessionController(BrowserSessionRegistry sessions, LocalRequestValidator requests) {
    this.sessions = sessions;
    this.requests = requests;
  }

  @PostMapping("/bootstrap")
  ResponseEntity<Void> bootstrap(
      HttpServletRequest request,
      @RequestHeader(value = "X-Hakamap-Bootstrap-Token", required = false) String bootstrapToken) {
    if (!requests.isLoopback(request)
        || !requests.hasValidHost(request)
        || !requests.hasSameOriginEvidence(request)) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN)
          .cacheControl(CacheControl.noStore())
          .build();
    }
    var session = sessions.establish(bootstrapToken);
    if (session.isEmpty()) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
          .cacheControl(CacheControl.noStore())
          .build();
    }
    ResponseCookie cookie =
        ResponseCookie.from(
                LocalApiSecurityFilter.SESSION_COOKIE, session.orElseThrow().sessionId())
            .httpOnly(true)
            .sameSite("Strict")
            .path("/")
            .build();
    return ResponseEntity.noContent()
        .header(HttpHeaders.SET_COOKIE, cookie.toString())
        .cacheControl(CacheControl.noStore())
        .build();
  }

  @GetMapping("/api/v1/session")
  ResponseEntity<SessionStatus> session(HttpServletRequest request) {
    String sessionId =
        java.util.Arrays.stream(
                java.util.Optional.ofNullable(request.getCookies())
                    .orElseGet(() -> new jakarta.servlet.http.Cookie[0]))
            .filter(cookie -> LocalApiSecurityFilter.SESSION_COOKIE.equals(cookie.getName()))
            .map(jakarta.servlet.http.Cookie::getValue)
            .findFirst()
            .orElse("");
    var session = sessions.authenticate(sessionId).orElseThrow();
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .header(LocalApiSecurityFilter.CSRF_HEADER, session.csrfToken())
        .body(new SessionStatus(true));
  }

  public record SessionStatus(boolean authenticated) {}
}
