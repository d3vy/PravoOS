package com.pravoos.notification.service;

import com.pravoos.notification.exception.NotificationDeliveryException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ChannelDelivery {

  private static final Logger log = LoggerFactory.getLogger(ChannelDelivery.class);

  private final ProcessedEventGuard processedEventGuard;

  public ChannelDelivery(ProcessedEventGuard processedEventGuard) {
    this.processedEventGuard = processedEventGuard;
  }

  public Batch batch(String eventType, String dedupKey) {
    return new Batch(eventType, dedupKey);
  }

  public final class Batch {

    private final String eventType;
    private final String dedupKey;
    private final List<String> failedChannels = new ArrayList<>();
    private RuntimeException firstFailure;

    private Batch(String eventType, String dedupKey) {
      this.eventType = eventType;
      this.dedupKey = dedupKey;
    }

    public Batch deliver(String channel, Runnable delivery) {
      reachedRecipient(
          channel,
          () -> {
            delivery.run();
            return true;
          });
      return this;
    }

    public boolean reachedRecipient(String channel, BooleanSupplier delivery) {
      String channelEvent = eventType + ":" + channel;
      if (!processedEventGuard.claim(channelEvent, dedupKey)) {
        boolean previouslyReached =
            processedEventGuard.previousOutcome(channelEvent, dedupKey).orElse(true);
        log.info(
            "Канал {} уже отработан для {} (доставлено: {}), пропускаю",
            channel,
            dedupKey,
            previouslyReached);
        return previouslyReached;
      }
      try {
        boolean reached = delivery.getAsBoolean();
        processedEventGuard.recordOutcome(channelEvent, dedupKey, reached);
        return reached;
      } catch (RuntimeException e) {
        processedEventGuard.release(channelEvent, dedupKey);
        failedChannels.add(channel);
        if (firstFailure == null) {
          firstFailure = e;
        }
        log.warn("Канал {} не доставлен для {}: {}", channel, dedupKey, e.getMessage());
        return false;
      }
    }

    public void complete() {
      if (failedChannels.isEmpty()) {
        return;
      }
      throw new NotificationDeliveryException(
          "Каналы " + String.join(", ", failedChannels) + " не доставлены для " + dedupKey,
          firstFailure);
    }
  }
}
