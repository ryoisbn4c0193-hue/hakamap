package jp.hakamap.project.domain.service;

import java.math.BigInteger;

public record NumberingRequest(String prefix, BigInteger start, int digits, String suffix) {
  public NumberingRequest {
    prefix = prefix == null ? "" : prefix;
    suffix = suffix == null ? "" : suffix;
    if (start == null || start.signum() < 0 || digits < 1 || digits > 10) {
      throw new IllegalArgumentException("invalid-numbering-request");
    }
  }
}
