package jp.hakamap.infrastructure.http;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.atomic.AtomicBoolean;
import jp.hakamap.infrastructure.lifecycle.ApplicationShutdownRequester;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "server.address=127.0.0.1")
class LocalHttpBoundaryIntegrationTest {
  @LocalServerPort private int port;

  @Autowired private BrowserSessionRegistry sessions;

  @Autowired private AtomicBoolean shutdownRequested;

  private CookieManager cookies;

  private HttpClient client;

  @BeforeEach
  void setUp() {
    sessions.invalidate();
    shutdownRequested.set(false);
    cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
    client =
        HttpClient.newBuilder()
            .cookieHandler(cookies)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
  }

  @Test
  void establishesHttpOnlySessionAndGetsCsrfWithoutOrigin() throws Exception {
    HttpResponse<String> bootstrap = establishSession();

    assertThat(bootstrap.statusCode()).isEqualTo(204);
    assertThat(bootstrap.headers().allValues(HttpHeaders.SET_COOKIE))
        .singleElement()
        .satisfies(
            value -> {
              assertThat(value).contains("HttpOnly");
              assertThat(value).contains("SameSite=Strict");
            });

    HttpResponse<String> session =
        send(
            HttpRequest.newBuilder(api("/api/v1/session"))
                .header("Sec-Fetch-Site", "same-origin")
                .GET()
                .build());

    assertThat(session.statusCode()).isEqualTo(200);
    assertThat(session.headers().firstValue("X-Hakamap-CSRF-Token")).isPresent();
    assertThat(session.headers().firstValue(HttpHeaders.CACHE_CONTROL)).contains("no-store");
    assertThat(session.body()).doesNotContain("csrf");
  }

  @Test
  void rejectsUnauthenticatedExternalOriginAndInvalidCsrf() throws Exception {
    HttpResponse<String> unauthenticated =
        send(
            HttpRequest.newBuilder(api("/api/v1/session"))
                .header("Sec-Fetch-Site", "same-origin")
                .GET()
                .build());
    assertThat(unauthenticated.statusCode()).isEqualTo(401);
    assertThat(unauthenticated.body()).contains("\"code\":\"session-required\"");

    establishSession();
    HttpResponse<String> externalOrigin =
        send(
            HttpRequest.newBuilder(api("/api/v1/session"))
                .header("Origin", "https://attacker.invalid")
                .GET()
                .build());
    assertThat(externalOrigin.statusCode()).isEqualTo(403);
    assertThat(externalOrigin.body()).contains("\"code\":\"local-request-rejected\"");

    HttpResponse<String> invalidCsrf =
        send(
            HttpRequest.newBuilder(api("/api/v1/application/exit"))
                .header("Origin", origin())
                .header("X-Hakamap-CSRF-Token", "invalid")
                .POST(HttpRequest.BodyPublishers.noBody())
                .build());
    assertThat(invalidCsrf.statusCode()).isEqualTo(403);
    assertThat(shutdownRequested).isFalse();
  }

  @Test
  void rejectsForgedDuplicateLaunchRequest() throws Exception {
    HttpResponse<String> response =
        sendWithoutCookies(
            HttpRequest.newBuilder(api("/api/internal/reopen"))
                .header("X-Hakamap-Instance-Id", "11111111-1111-4111-8111-111111111111")
                .header("X-Hakamap-Control-Token", "forged-control-token")
                .POST(HttpRequest.BodyPublishers.noBody())
                .build());

    assertThat(response.statusCode()).isEqualTo(401);
    assertThat(response.headers().firstValue(HttpHeaders.CACHE_CONTROL)).contains("no-store");
  }

  @Test
  void rejectsMissingAndReusedBootstrapToken() throws Exception {
    HttpResponse<String> missing =
        send(
            HttpRequest.newBuilder(api("/bootstrap"))
                .header("Origin", origin())
                .POST(HttpRequest.BodyPublishers.noBody())
                .build());
    assertThat(missing.statusCode()).isEqualTo(401);

    String token = sessions.issueBootstrapToken();
    HttpRequest request =
        HttpRequest.newBuilder(api("/bootstrap"))
            .header("Origin", origin())
            .header("X-Hakamap-Bootstrap-Token", token)
            .POST(HttpRequest.BodyPublishers.noBody())
            .build();
    assertThat(send(request).statusCode()).isEqualTo(204);
    assertThat(send(request).statusCode()).isEqualTo(401);
  }

  @Test
  void invalidatesOldSessionAndCsrfWhenSessionIsRegenerated() throws Exception {
    establishSession();
    String oldCookie = sessionCookie();
    String oldCsrf = csrfToken();

    establishSession();
    String newCsrf = csrfToken();
    assertThat(newCsrf).isNotEqualTo(oldCsrf);

    HttpResponse<String> oldSession =
        sendWithoutCookies(
            HttpRequest.newBuilder(api("/api/v1/session"))
                .header("Cookie", oldCookie)
                .header("Sec-Fetch-Site", "same-origin")
                .GET()
                .build());
    assertThat(oldSession.statusCode()).isEqualTo(401);

    HttpResponse<String> oldCsrfRequest =
        send(
            HttpRequest.newBuilder(api("/api/v1/application/exit"))
                .header("Origin", origin())
                .header("X-Hakamap-CSRF-Token", oldCsrf)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build());
    assertThat(oldCsrfRequest.statusCode()).isEqualTo(403);

    HttpResponse<String> validRequest =
        send(
            HttpRequest.newBuilder(api("/api/v1/application/exit"))
                .header("Origin", origin())
                .header("X-Hakamap-CSRF-Token", newCsrf)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build());
    assertThat(validRequest.statusCode()).isEqualTo(202);
    assertThat(shutdownRequested).isTrue();
  }

  private HttpResponse<String> establishSession() throws Exception {
    String bootstrapToken = sessions.issueBootstrapToken();
    return send(
        HttpRequest.newBuilder(api("/bootstrap"))
            .header("Origin", origin())
            .header("X-Hakamap-Bootstrap-Token", bootstrapToken)
            .POST(HttpRequest.BodyPublishers.noBody())
            .build());
  }

  private String csrfToken() throws Exception {
    HttpResponse<String> response =
        send(
            HttpRequest.newBuilder(api("/api/v1/session"))
                .header("Sec-Fetch-Site", "same-origin")
                .GET()
                .build());
    assertThat(response.statusCode()).isEqualTo(200);
    return response.headers().firstValue("X-Hakamap-CSRF-Token").orElseThrow();
  }

  private String sessionCookie() {
    return cookies.getCookieStore().getCookies().stream()
        .filter(cookie -> LocalApiSecurityFilter.SESSION_COOKIE.equals(cookie.getName()))
        .map(cookie -> cookie.getName() + "=" + cookie.getValue())
        .findFirst()
        .orElseThrow();
  }

  private HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException {
    return client.send(request, HttpResponse.BodyHandlers.ofString());
  }

  private HttpResponse<String> sendWithoutCookies(HttpRequest request)
      throws IOException, InterruptedException {
    return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
  }

  private URI api(String path) {
    return URI.create(origin() + path);
  }

  private String origin() {
    return "http://127.0.0.1:" + port;
  }

  @TestConfiguration
  static class ShutdownTestConfiguration {
    @Bean
    AtomicBoolean shutdownRequested() {
      return new AtomicBoolean();
    }

    @Bean
    @Primary
    ApplicationShutdownRequester testShutdownRequester(AtomicBoolean requested) {
      return () -> requested.set(true);
    }
  }
}
