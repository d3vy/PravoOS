package com.pravoos.common.web.internal;

import com.pravoos.common.security.internal.InternalCallerCheck;
import com.pravoos.common.security.internal.InternalCallerProperties;
import com.pravoos.common.security.internal.InternalCallerVerifier;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
public class InternalCallerSecurityConfiguration {

  @Bean
  public InternalCallerVerifier internalCallerVerifier(InternalCallerProperties callerProperties) {
    return new InternalCallerVerifier(callerProperties);
  }

  @Bean
  @Profile("docker")
  public InitializingBean internalCallerGuard(InternalCallerProperties callerProperties) {
    return () -> InternalCallerCheck.requireEveryCallerConfigured(callerProperties);
  }
}
