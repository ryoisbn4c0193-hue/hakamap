package jp.hakamap;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;

@SpringBootTest
class HakamapApplicationTests {

  @Test
  void contextLoads() {}

  @Test
  void frontendIsPackagedAsStaticResource() {
    assertThat(new ClassPathResource("static/index.html").exists()).isTrue();
  }
}
