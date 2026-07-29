package jp.hakamap.infrastructure.fileselection;

import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FileSelectionConfiguration {
  @Bean
  @ConditionalOnMissingBean(FileChooserGateway.class)
  FileChooserGateway fileChooserGateway() {
    return new SwingFileChooserGateway();
  }

  @Bean
  FileSelectionService fileSelectionService(FileChooserGateway chooser, Clock clock) {
    return new FileSelectionService(chooser, clock);
  }
}
