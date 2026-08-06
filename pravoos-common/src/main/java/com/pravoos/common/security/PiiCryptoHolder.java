package com.pravoos.common.security;

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
    synchronized (PiiCryptoHolder.class) {
      applicationContext = context;
      encryptor = null;
    }
  }

  public static PiiEncryptor encryptor() {
    PiiEncryptor current = encryptor;
    if (current != null) {
      return current;
    }
    synchronized (PiiCryptoHolder.class) {
      if (encryptor != null) {
        return encryptor;
      }
      ApplicationContext context = applicationContext;
      if (context == null) {
        throw new IllegalStateException("PII crypto context is not initialized yet");
      }
      encryptor = context.getBean(PiiEncryptor.class);
      return encryptor;
    }
  }
}
