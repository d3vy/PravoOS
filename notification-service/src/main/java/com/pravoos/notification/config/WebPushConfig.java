package com.pravoos.notification.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pravoos.notification.push.NoopWebPushSender;
import com.pravoos.notification.push.VapidWebPushSender;
import com.pravoos.notification.push.WebPushSender;
import java.security.GeneralSecurityException;
import java.security.Security;
import nl.martijndwars.webpush.PushService;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(WebPushProperties.class)
public class WebPushConfig {

  private static final Logger log = LoggerFactory.getLogger(WebPushConfig.class);

  @Bean
  public WebPushSender webPushSender(WebPushProperties properties, ObjectMapper objectMapper) {
    if (!properties.configured()) {
      log.warn("VAPID keys are not configured, web push notifications are disabled");
      return new NoopWebPushSender();
    }
    Security.addProvider(new BouncyCastleProvider());
    try {
      PushService pushService =
          new PushService(properties.publicKey(), properties.privateKey(), properties.subject());
      log.info("Web push enabled with VAPID subject {}", properties.subject());
      return new VapidWebPushSender(pushService, objectMapper);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Invalid VAPID key pair for web push", e);
    }
  }
}
