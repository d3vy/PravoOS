package com.pravoos.observability.sentry;

import com.pravoos.observability.logging.PiiScrubber;
import io.sentry.Hint;
import io.sentry.SentryEvent;
import io.sentry.SentryOptions;
import io.sentry.protocol.Message;
import io.sentry.protocol.Request;
import io.sentry.protocol.SentryException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.MDC;

public class SentryEventEnricher implements SentryOptions.BeforeSendCallback {

  private static final Set<String> SENSITIVE_HEADERS =
      Set.of("authorization", "cookie", "set-cookie", "x-internal-secret", "x-application-token");
  private static final List<String> CORRELATION_KEYS = List.of("requestId", "traceId", "spanId");

  private final String serviceName;
  private final PiiScrubber scrubber;
  private final List<SentryEventPolicy> policies;

  public SentryEventEnricher(
      String serviceName, PiiScrubber scrubber, List<SentryEventPolicy> policies) {
    this.serviceName = serviceName;
    this.scrubber = scrubber;
    this.policies = policies;
  }

  @Override
  public SentryEvent execute(SentryEvent event, Hint hint) {
    if (policies.stream().anyMatch(policy -> !policy.shouldReport(event))) {
      return null;
    }
    event.setTag("service", serviceName);
    CORRELATION_KEYS.forEach(key -> tagFromMdc(event, key));
    scrubMessage(event.getMessage());
    scrubExceptions(event.getExceptions());
    scrubRequest(event.getRequest());
    return event;
  }

  private void tagFromMdc(SentryEvent event, String key) {
    String value = MDC.get(key);
    if (value != null && !value.isBlank()) {
      event.setTag(key, value);
    }
  }

  private void scrubMessage(Message message) {
    if (message == null) {
      return;
    }
    message.setFormatted(scrubber.scrub(message.getFormatted()));
    message.setMessage(scrubber.scrub(message.getMessage()));
    if (message.getParams() != null) {
      message.setParams(message.getParams().stream().map(scrubber::scrub).toList());
    }
  }

  private void scrubExceptions(List<SentryException> exceptions) {
    if (exceptions == null) {
      return;
    }
    exceptions.forEach(exception -> exception.setValue(scrubber.scrub(exception.getValue())));
  }

  private void scrubRequest(Request request) {
    if (request == null) {
      return;
    }
    request.setCookies(null);
    request.setUrl(scrubber.scrub(request.getUrl()));
    request.setQueryString(scrubber.scrub(request.getQueryString()));
    Map<String, String> headers = request.getHeaders();
    if (headers != null) {
      request.setHeaders(
          headers.entrySet().stream()
              .filter(entry -> !SENSITIVE_HEADERS.contains(entry.getKey().toLowerCase(Locale.ROOT)))
              .collect(
                  Collectors.toMap(Map.Entry::getKey, entry -> scrubber.scrub(entry.getValue()))));
    }
  }
}
