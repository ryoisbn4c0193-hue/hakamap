package jp.hakamap.infrastructure.http;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class LocalRequestValidatorTest {
  private final LocalRequestValidator validator = new LocalRequestValidator();

  @Test
  void acceptsExactLoopbackHostAndSameOriginReferer() {
    MockHttpServletRequest request = request();
    request.addHeader("Referer", "http://127.0.0.1:54321/map");

    assertThat(validator.isLoopback(request)).isTrue();
    assertThat(validator.hasValidHost(request)).isTrue();
    assertThat(validator.hasSameOriginEvidence(request)).isTrue();
  }

  @Test
  void rejectsLanRemoteAddressWrongHostAndCrossSiteRequest() {
    MockHttpServletRequest request = request();
    request.setRemoteAddr("192.168.1.10");
    request.removeHeader("Host");
    request.addHeader("Host", "localhost:54321");
    request.addHeader("Sec-Fetch-Site", "cross-site");

    assertThat(validator.isLoopback(request)).isFalse();
    assertThat(validator.hasValidHost(request)).isFalse();
    assertThat(validator.hasSameOriginEvidence(request)).isFalse();
  }

  private MockHttpServletRequest request() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr("127.0.0.1");
    request.setLocalPort(54321);
    request.addHeader("Host", "127.0.0.1:54321");
    return request;
  }
}
