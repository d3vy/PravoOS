package com.pravoos.user.identity.internal.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
  JwtProperties.class,
  RefreshCookieProperties.class,
  BruteForceProperties.class,
  TestLawyerProperties.class,
  PasswordPolicyProperties.class,
  MfaProperties.class
})
public class IdentityPropertiesConfig {}
