package jp.hakamap.infrastructure.lifecycle;

import java.time.Clock;
import jp.hakamap.infrastructure.http.BrowserSessionRegistry;
import jp.hakamap.infrastructure.http.SecureTokenGenerator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LifecycleConfiguration {
  @Bean
  @ConditionalOnBean(RuntimeLease.class)
  RuntimeLifecycleService runtimeLifecycleService(
      RuntimeLease lease,
      BrowserLauncher browser,
      BrowserSessionRegistry sessions,
      SecureTokenGenerator tokens,
      Clock clock) {
    return new RuntimeLifecycleService(lease, browser, sessions, tokens, clock);
  }

  @Bean
  @ConditionalOnMissingBean(ApplicationShutdownRequester.class)
  ApplicationShutdownRequester applicationShutdownRequester(
      ConfigurableApplicationContext context) {
    return new SpringApplicationShutdownRequester(context);
  }
}
