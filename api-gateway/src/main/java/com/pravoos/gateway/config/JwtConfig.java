package com.pravoos.gateway.config;

import com.pravoos.common.security.JwtVerifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JwtConfig {

  @Bean
  public JwtVerifier jwtVerifier(JwtProperties jwtProperties) {
    return new JwtVerifier(jwtProperties.publicKey());
  }
}
