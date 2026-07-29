package jp.hakamap.infrastructure.lifecycle;

import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import jp.hakamap.infrastructure.http.BrowserSessionRegistry;
import jp.hakamap.infrastructure.http.SecureTokenGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.server.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;

public final class RuntimeLifecycleService
    implements ApplicationListener<WebServerInitializedEvent>, AutoCloseable {
  private static final Logger LOGGER = LoggerFactory.getLogger(RuntimeLifecycleService.class);

  private final RuntimeLease lease;

  private final BrowserLauncher browser;

  private final BrowserSessionRegistry sessions;

  private final Clock clock;

  private final UUID instanceId;

  private final Instant startedAt;

  private final String controlToken;

  private final ScheduledExecutorService heartbeat;

  private volatile int port;

  public RuntimeLifecycleService(
      RuntimeLease lease,
      BrowserLauncher browser,
      BrowserSessionRegistry sessions,
      SecureTokenGenerator tokens,
      Clock clock) {
    this.lease = lease;
    this.browser = browser;
    this.sessions = sessions;
    this.clock = clock;
    this.instanceId = UUID.randomUUID();
    this.startedAt = clock.instant();
    this.controlToken = tokens.next();
    this.heartbeat =
        Executors.newSingleThreadScheduledExecutor(
            runnable -> {
              Thread thread = new Thread(runnable, "hakamap-runtime-heartbeat");
              thread.setDaemon(true);
              return thread;
            });
    lease.writeMarker(marker(startedAt));
  }

  @Override
  public void onApplicationEvent(WebServerInitializedEvent event) {
    port = event.getWebServer().getPort();
    lease.writeInstance(
        new RuntimeInstance(
            ProcessHandle.current().pid(), instanceId, port, startedAt, controlToken));
    heartbeat.scheduleAtFixedRate(this::updateHeartbeat, 5, 5, TimeUnit.SECONDS);
    openBrowser();
  }

  public boolean authenticateControl(String requestedInstanceId, String requestedControlToken) {
    return constantTimeEquals(instanceId.toString(), requestedInstanceId)
        && constantTimeEquals(controlToken, requestedControlToken);
  }

  public void openBrowser() {
    String bootstrap = sessions.issueBootstrapToken();
    browser.open(URI.create("http://127.0.0.1:" + port + "/#bootstrap=" + bootstrap));
  }

  public UUID instanceId() {
    return instanceId;
  }

  private void updateHeartbeat() {
    try {
      lease.writeMarker(marker(clock.instant()));
    } catch (LifecycleException ignored) {
      LOGGER.warn("runtime-heartbeat-write-failed");
    }
  }

  private UncleanExitMarker marker(Instant heartbeatAt) {
    return new UncleanExitMarker(instanceId, "0.0.1-SNAPSHOT", startedAt, heartbeatAt);
  }

  private boolean constantTimeEquals(String expected, String actual) {
    return actual != null
        && java.security.MessageDigest.isEqual(
            expected.getBytes(java.nio.charset.StandardCharsets.UTF_8),
            actual.getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  @Override
  @PreDestroy
  public void close() {
    sessions.invalidate();
    heartbeat.shutdownNow();
    lease.close();
  }
}
