package com.pravoos.ai.shared.security;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

@Component
public class PiiCryptoHolder implements ApplicationContextAware {

  private static volatile ApplicationContext applicationContext;
  private static volatile PiiEncryptor encryptor;

  @Override
  public void setApplicationContext(ApplicationContext context) throws BeansException {
    applicationContext = context;
  }

  public static PiiEncryptor encryptor() {
    PiiEncryptor current = encryptor;
    if (current != null) {
      return current;
    }
    ApplicationContext context = applicationContext;
    if (context == null) {
      throw new IllegalStateException("PII crypto context is not initialized yet");
    }
    current = context.getBean(PiiEncryptor.class);
    encryptor = current;
    return current;
  }
}
