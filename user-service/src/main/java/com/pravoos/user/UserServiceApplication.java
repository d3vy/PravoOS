package com.pravoos.user;

import com.pravoos.common.security.PiiCryptoProperties;
import com.pravoos.common.security.internal.InternalCallerProperties;
import com.pravoos.common.web.ClientIpSanitizingFilter;
import com.pravoos.common.web.RequestIdFilter;
import com.pravoos.common.web.UtcTimestampModule;
import com.pravoos.user.shared.config.AdminProperties;
import com.pravoos.user.shared.config.ResendProperties;
import org.springframework.beans.factory.annotation.Value;
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
  InternalCallerProperties.class,
  ResendProperties.class,
  PiiCryptoProperties.class
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

  @Bean
  public UtcTimestampModule utcTimestampModule() {
    return new UtcTimestampModule();
  }

  @Bean
  public ClientIpSanitizingFilter clientIpSanitizingFilter(
      @Value("${app.trusted-peers:" + ClientIpSanitizingFilter.DEFAULT_TRUSTED_PEERS + "}")
          String trustedPeers) {
    return new ClientIpSanitizingFilter(trustedPeers);
  }
}
