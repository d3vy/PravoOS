package com.pravoos.llm.pii;

import com.pravoos.llm.config.PiiRedactionProperties;
import com.pravoos.llm.domain.LlmMessage;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.regex.Matcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class PromptPiiRedactor {

  private static final Logger log = LoggerFactory.getLogger(PromptPiiRedactor.class);

  private final PiiRedactionProperties properties;
  private final Counter redactedCounter;

  public PromptPiiRedactor(PiiRedactionProperties properties, MeterRegistry meterRegistry) {
    this.properties = properties;
    this.redactedCounter = Counter.builder("pravoos.llm.pii.redacted").register(meterRegistry);
    if (!properties.isEnabled()) {
      log.warn(
          "Prompt PII redaction is DISABLED — personal data leaves the country unmasked. "
              + "This is lawful only if cross-border transfer is separately covered.");
    }
  }

  public RedactionSession newSession() {
    return new RedactionSession();
  }

  public String redactForEmbedding(String text, RedactionSession session) {
    return properties.isEmbeddingsEnabled() ? redact(text, session) : text;
  }

  public String redact(String text, RedactionSession session) {
    if (!properties.isEnabled() || text == null || text.isEmpty()) {
      return text;
    }
    String redacted = text;
    for (PiiPattern pii : PiiPattern.values()) {
      if (!properties.isCategoryEnabled(pii)) {
        continue;
      }
      redacted = replaceAll(redacted, pii, session);
    }
    return redacted;
  }

  public List<LlmMessage> redact(List<LlmMessage> messages, RedactionSession session) {
    if (!properties.isEnabled() || messages == null || messages.isEmpty()) {
      return messages;
    }
    return messages.stream()
        .map(message -> new LlmMessage(message.role(), redact(message.content(), session)))
        .toList();
  }

  public void recordSession(RedactionSession session) {
    if (session.isEmpty()) {
      return;
    }
    redactedCounter.increment(session.size());
    log.debug("Redacted {} personal data value(s) before sending prompt", session.size());
  }

  private String replaceAll(String text, PiiPattern pii, RedactionSession session) {
    Matcher matcher = pii.pattern().matcher(text);
    if (!matcher.find()) {
      return text;
    }
    StringBuilder result = new StringBuilder();
    matcher.reset();
    while (matcher.find()) {
      String value = matcher.group();
      matcher.appendReplacement(
          result, Matcher.quoteReplacement(session.placeholderFor(pii.label(), value)));
    }
    matcher.appendTail(result);
    return result.toString();
  }
}
