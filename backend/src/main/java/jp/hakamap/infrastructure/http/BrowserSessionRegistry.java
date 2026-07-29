package jp.hakamap.infrastructure.http;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class BrowserSessionRegistry {
  static final Duration BOOTSTRAP_LIFETIME = Duration.ofMinutes(1);

  private final SecureTokenGenerator tokens;

  private final Clock clock;

  private final Map<String, Instant> bootstrapTokens = new ConcurrentHashMap<>();

  private volatile BrowserSession currentSession;

  public BrowserSessionRegistry(SecureTokenGenerator tokens, Clock clock) {
    this.tokens = tokens;
    this.clock = clock;
  }

  public String issueBootstrapToken() {
    discardExpiredBootstrapTokens();
    String token = tokens.next();
    bootstrapTokens.put(token, clock.instant().plus(BOOTSTRAP_LIFETIME));
    return token;
  }

  public Optional<BrowserSession> establish(String bootstrapToken) {
    if (bootstrapToken == null) {
      return Optional.empty();
    }
    Instant expiresAt = bootstrapTokens.remove(bootstrapToken);
    if (expiresAt == null || !clock.instant().isBefore(expiresAt)) {
      return Optional.empty();
    }
    BrowserSession session = new BrowserSession(tokens.next(), tokens.next());
    currentSession = session;
    bootstrapTokens.clear();
    return Optional.of(session);
  }

  public Optional<BrowserSession> authenticate(String sessionId) {
    BrowserSession session = currentSession;
    if (session == null || !constantTimeEquals(session.sessionId(), sessionId)) {
      return Optional.empty();
    }
    return Optional.of(session);
  }

  public void invalidate() {
    currentSession = null;
    bootstrapTokens.clear();
  }

  private void discardExpiredBootstrapTokens() {
    Instant now = clock.instant();
    bootstrapTokens.entrySet().removeIf(entry -> !now.isBefore(entry.getValue()));
  }

  private boolean constantTimeEquals(String expected, String actual) {
    if (actual == null) {
      return false;
    }
    return java.security.MessageDigest.isEqual(
        expected.getBytes(java.nio.charset.StandardCharsets.UTF_8),
        actual.getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  public record BrowserSession(String sessionId, String csrfToken) {}
}
