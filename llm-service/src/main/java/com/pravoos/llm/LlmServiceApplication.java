package com.pravoos.llm;

import com.pravoos.common.web.RequestIdFilter;
import com.pravoos.llm.config.InternalSecretProperties;
import com.pravoos.llm.config.OpenAiProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@EnableConfigurationProperties({OpenAiProperties.class, InternalSecretProperties.class})
public class LlmServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(LlmServiceApplication.class, args);
  }

  @Bean
  public RequestIdFilter requestIdFilter() {
    return new RequestIdFilter();
  }
}
