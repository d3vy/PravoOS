package com.pravoos.observability.sentry;

import static org.assertj.core.api.Assertions.assertThat;

import com.pravoos.observability.logging.PiiScrubber;
import io.sentry.Hint;
import io.sentry.SentryEvent;
import io.sentry.protocol.Message;
import io.sentry.protocol.Request;
import io.sentry.protocol.SentryException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class SentryEventEnricherTest {

  private static final Hint HINT = new Hint();

  private SentryEventEnricher enricher;

  @BeforeEach
  void setup() {
    enricher = new SentryEventEnricher("ai-service", new PiiScrubber(), List.of());
  }

  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  @Test
  void tagsEventWithServiceAndCorrelationIds() {
    MDC.put("requestId", "req-1");
    MDC.put("traceId", "trace-1");

    SentryEvent enriched = enricher.execute(new SentryEvent(), HINT);

    assertThat(enriched.getTags())
        .containsEntry("service", "ai-service")
        .containsEntry("requestId", "req-1")
        .containsEntry("traceId", "trace-1")
        .doesNotContainKey("spanId");
  }

  @Test
  void scrubsPiiFromMessageAndException() {
    SentryEvent event = new SentryEvent();
    Message message = new Message();
    message.setFormatted("payment failed for lawyer@pravoos.ru");
    event.setMessage(message);
    SentryException exception = new SentryException();
    exception.setValue("token eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.signature-value rejected");
    event.setExceptions(List.of(exception));

    SentryEvent enriched = enricher.execute(event, HINT);

    assertThat(enriched.getMessage().getFormatted())
        .isEqualTo("payment failed for la***@pravoos.ru");
    assertThat(enriched.getExceptions().getFirst().getValue())
        .isEqualTo("token [redacted] rejected");
  }

  @Test
  void stripsCredentialsFromRequest() {
    SentryEvent event = new SentryEvent();
    Request request = new Request();
    request.setCookies("refreshToken=secret");
    request.setHeaders(Map.of("Authorization", "Bearer abcdef0123456789", "X-Request-Id", "req-2"));
    request.setQueryString("email=lawyer@pravoos.ru");
    event.setRequest(request);

    Request enriched = enricher.execute(event, HINT).getRequest();

    assertThat(enriched.getCookies()).isNull();
    assertThat(enriched.getHeaders()).containsOnlyKeys("X-Request-Id");
    assertThat(enriched.getQueryString()).isEqualTo("email=la***@pravoos.ru");
  }

  @Test
  void dropsEventRejectedByPolicy() {
    SentryEventEnricher filtering =
        new SentryEventEnricher("ai-service", new PiiScrubber(), List.of(event -> false));

    assertThat(filtering.execute(new SentryEvent(), HINT)).isNull();
  }
}
