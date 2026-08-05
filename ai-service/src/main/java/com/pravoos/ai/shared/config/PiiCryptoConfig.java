package com.pravoos.ai.shared.config;

import com.pravoos.common.security.PiiCryptoHolder;
import com.pravoos.common.security.PiiCryptoProperties;
import com.pravoos.common.security.PiiEncryptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PiiCryptoConfig {

  @Bean
  public PiiEncryptor piiEncryptor(PiiCryptoProperties properties) {
    return new PiiEncryptor(properties);
  }

  @Bean
  public PiiCryptoHolder piiCryptoHolder() {
    return new PiiCryptoHolder();
  }
}
