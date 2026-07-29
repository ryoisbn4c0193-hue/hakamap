package jp.hakamap.infrastructure.http;

import java.security.SecureRandom;
import java.util.Base64;

public final class SecureTokenGenerator {
  private static final int TOKEN_BYTES = 32;

  private final SecureRandom random;

  public SecureTokenGenerator() {
    this(new SecureRandom());
  }

  SecureTokenGenerator(SecureRandom random) {
    this.random = random;
  }

  public String next() {
    byte[] bytes = new byte[TOKEN_BYTES];
    random.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }
}
