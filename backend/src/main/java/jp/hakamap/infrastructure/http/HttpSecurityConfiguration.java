package jp.hakamap.infrastructure.http;

import java.time.Clock;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class HttpSecurityConfiguration {
  @Bean
  SecureTokenGenerator secureTokenGenerator() {
    return new SecureTokenGenerator();
  }

  @Bean
  Clock systemClock() {
    return Clock.systemUTC();
  }

  @Bean
  BrowserSessionRegistry browserSessionRegistry(SecureTokenGenerator tokens, Clock clock) {
    return new BrowserSessionRegistry(tokens, clock);
  }

  @Bean
  LocalRequestValidator localRequestValidator() {
    return new LocalRequestValidator();
  }

  @Bean
  HttpProblemWriter httpProblemWriter(JsonMapper jsonMapper) {
    return new HttpProblemWriter(jsonMapper);
  }

  @Bean
  FilterRegistrationBean<LocalApiSecurityFilter> localApiSecurityFilter(
      BrowserSessionRegistry sessions, LocalRequestValidator requests, HttpProblemWriter problems) {
    FilterRegistrationBean<LocalApiSecurityFilter> registration = new FilterRegistrationBean<>();
    registration.setFilter(new LocalApiSecurityFilter(sessions, requests, problems));
    registration.setOrder(1);
    registration.addUrlPatterns("/api/*");
    return registration;
  }
}
