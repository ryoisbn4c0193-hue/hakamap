package jp.hakamap.project.application.history;

import java.util.Objects;

public record StateFingerprint(String sha256) {
  public StateFingerprint {
    Objects.requireNonNull(sha256, "sha256");
    if (!sha256.matches("^[0-9a-f]{64}$")) {
      throw new IllegalArgumentException("invalid-state-fingerprint");
    }
  }
}
