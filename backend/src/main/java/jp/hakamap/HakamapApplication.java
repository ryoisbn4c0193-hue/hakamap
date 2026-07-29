package jp.hakamap;

import jp.hakamap.infrastructure.lifecycle.ApplicationLauncher;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class HakamapApplication {

  public static void main(String[] args) {
    ApplicationLauncher.launch(args);
  }
}
