package jp.hakamap.infrastructure.http;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

public final class LocalApiSecurityFilter extends OncePerRequestFilter {
  public static final String SESSION_COOKIE = "HAKAMAP_SESSION";

  public static final String CSRF_HEADER = "X-Hakamap-CSRF-Token";

  public static final String AUTHENTICATED_SESSION_ATTRIBUTE =
      LocalApiSecurityFilter.class.getName() + ".sessionId";

  private final BrowserSessionRegistry sessions;

  private final LocalRequestValidator requests;

  private final HttpProblemWriter problems;

  public LocalApiSecurityFilter(
      BrowserSessionRegistry sessions, LocalRequestValidator requests, HttpProblemWriter problems) {
    this.sessions = sessions;
    this.requests = requests;
    this.problems = problems;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    return !path.startsWith("/api/") || path.startsWith("/api/internal/");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    if (!requests.isLoopback(request)
        || !requests.hasValidHost(request)
        || !requests.hasSameOriginEvidence(request)) {
      problems.write(response, HttpStatus.FORBIDDEN, "local-request-rejected");
      return;
    }
    String sessionId = cookie(request, SESSION_COOKIE);
    var session = sessions.authenticate(sessionId);
    if (session.isEmpty()) {
      problems.write(response, HttpStatus.UNAUTHORIZED, "session-required");
      return;
    }
    if (isStateChanging(request)
        && !constantTimeEquals(session.orElseThrow().csrfToken(), request.getHeader(CSRF_HEADER))) {
      problems.write(response, HttpStatus.FORBIDDEN, "csrf-token-invalid");
      return;
    }
    request.setAttribute(AUTHENTICATED_SESSION_ATTRIBUTE, session.orElseThrow().sessionId());
    filterChain.doFilter(request, response);
  }

  private String cookie(HttpServletRequest request, String name) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) {
      return null;
    }
    return Arrays.stream(cookies)
        .filter(cookie -> name.equals(cookie.getName()))
        .map(Cookie::getValue)
        .findFirst()
        .orElse(null);
  }

  private boolean isStateChanging(HttpServletRequest request) {
    return !HttpMethod.GET.matches(request.getMethod())
        && !HttpMethod.HEAD.matches(request.getMethod())
        && !HttpMethod.OPTIONS.matches(request.getMethod());
  }

  private boolean constantTimeEquals(String expected, String actual) {
    return actual != null
        && java.security.MessageDigest.isEqual(
            expected.getBytes(java.nio.charset.StandardCharsets.UTF_8),
            actual.getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }
}
