package jp.hakamap.infrastructure.http;

import jakarta.servlet.http.HttpServletRequest;

public final class LocalRequestValidator {
  public boolean isLoopback(HttpServletRequest request) {
    return "127.0.0.1".equals(request.getRemoteAddr());
  }

  public boolean hasValidHost(HttpServletRequest request) {
    return expectedAuthority(request).equals(request.getHeader("Host"));
  }

  public boolean hasSameOriginEvidence(HttpServletRequest request) {
    String expectedOrigin = "http://" + expectedAuthority(request);
    String origin = request.getHeader("Origin");
    if (origin != null) {
      return expectedOrigin.equals(origin);
    }
    if ("cross-site".equalsIgnoreCase(request.getHeader("Sec-Fetch-Site"))) {
      return false;
    }
    if ("same-origin".equalsIgnoreCase(request.getHeader("Sec-Fetch-Site"))) {
      return true;
    }
    String referer = request.getHeader("Referer");
    return referer != null
        && (referer.equals(expectedOrigin) || referer.startsWith(expectedOrigin + "/"));
  }

  private String expectedAuthority(HttpServletRequest request) {
    return "127.0.0.1:" + request.getLocalPort();
  }
}
