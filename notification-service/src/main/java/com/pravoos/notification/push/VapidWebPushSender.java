package com.pravoos.notification.push;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pravoos.notification.client.PushSubscriptionResponse;
import java.nio.charset.StandardCharsets;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import nl.martijndwars.webpush.Urgency;
import org.apache.http.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VapidWebPushSender implements WebPushSender {

  private static final Logger log = LoggerFactory.getLogger(VapidWebPushSender.class);
  private static final int GONE = 410;
  private static final int NOT_FOUND = 404;

  private final PushService pushService;
  private final ObjectMapper objectMapper;

  public VapidWebPushSender(PushService pushService, ObjectMapper objectMapper) {
    this.pushService = pushService;
    this.objectMapper = objectMapper;
  }

  @Override
  public PushDeliveryStatus send(PushSubscriptionResponse subscription, PushMessage message) {
    try {
      Notification notification =
          Notification.builder()
              .endpoint(subscription.endpoint())
              .userPublicKey(subscription.p256dh())
              .userAuth(subscription.auth())
              .payload(serialize(message))
              .ttl(message.ttlSeconds())
              .urgency(Urgency.NORMAL)
              .build();
      HttpResponse response = pushService.send(notification);
      return classify(response.getStatusLine().getStatusCode(), subscription);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.warn("Web push interrupted for endpoint {}", maskEndpoint(subscription.endpoint()));
      return PushDeliveryStatus.FAILED;
    } catch (Exception e) {
      log.error(
          "Web push failed for endpoint {}: {}",
          maskEndpoint(subscription.endpoint()),
          e.getMessage());
      return PushDeliveryStatus.FAILED;
    }
  }

  private PushDeliveryStatus classify(int statusCode, PushSubscriptionResponse subscription) {
    if (statusCode >= 200 && statusCode < 300) {
      return PushDeliveryStatus.DELIVERED;
    }
    if (statusCode == GONE || statusCode == NOT_FOUND) {
      log.info("Push endpoint expired ({}): {}", statusCode, maskEndpoint(subscription.endpoint()));
      return PushDeliveryStatus.EXPIRED;
    }
    log.warn(
        "Push provider rejected message with status {} for endpoint {}",
        statusCode,
        maskEndpoint(subscription.endpoint()));
    return PushDeliveryStatus.FAILED;
  }

  private byte[] serialize(PushMessage message) throws JsonProcessingException {
    return objectMapper.writeValueAsString(message).getBytes(StandardCharsets.UTF_8);
  }

  private String maskEndpoint(String endpoint) {
    if (endpoint == null || endpoint.length() <= 32) {
      return "***";
    }
    return endpoint.substring(0, 32) + "...";
  }
}
