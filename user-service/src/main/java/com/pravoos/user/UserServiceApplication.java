package com.pravoos.user;

import com.pravoos.common.web.RequestIdFilter;
import com.pravoos.user.shared.config.AdminProperties;
import com.pravoos.user.shared.config.InternalSecretProperties;
import com.pravoos.user.shared.config.ResendProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@EnableConfigurationProperties({
  AdminProperties.class,
  InternalSecretProperties.class,
  ResendProperties.class
})
@EnableScheduling
@EnableAsync
public class UserServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(UserServiceApplication.class, args);
  }

  @Bean
  public RequestIdFilter requestIdFilter() {
    return new RequestIdFilter();
  }
}
