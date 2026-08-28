package com.pravoos.llm.pii;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.pravoos.llm.config.PiiRedactionProperties;
import com.pravoos.llm.domain.LlmMessage;
import com.pravoos.llm.domain.LlmToolCall;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class PromptPiiRedactor {

  private static final Logger log = LoggerFactory.getLogger(PromptPiiRedactor.class);

  private final PiiRedactionProperties properties;
  private final List<PiiPattern> enabledPatterns;
  private final Counter redactedCounter;
  private final ObjectMapper objectMapper;

  public PromptPiiRedactor(
      PiiRedactionProperties properties, MeterRegistry meterRegistry, ObjectMapper objectMapper) {
    this.properties = properties;
    this.objectMapper = objectMapper;
    this.enabledPatterns = List.copyOf(properties.enabledCategories());
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
    for (PiiPattern pii : enabledPatterns) {
      redacted = replaceAll(redacted, pii, session);
    }
    return redacted;
  }

  public List<LlmMessage> redact(List<LlmMessage> messages, RedactionSession session) {
    if (!properties.isEnabled() || messages == null || messages.isEmpty()) {
      return messages;
    }
    return messages.stream().map(message -> redact(message, session)).toList();
  }

  public LlmMessage redact(LlmMessage message, RedactionSession session) {
    if (!properties.isEnabled() || message == null) {
      return message;
    }
    return new LlmMessage(
        message.role(),
        redact(message.content(), session),
        redactToolCalls(message.toolCalls(), session),
        message.toolCallId());
  }

  public List<LlmToolCall> redactToolCalls(List<LlmToolCall> toolCalls, RedactionSession session) {
    if (toolCalls == null || toolCalls.isEmpty()) {
      return List.of();
    }
    return toolCalls.stream()
        .map(toolCall -> toolCall.withArgumentsJson(redactJson(toolCall.argumentsJson(), session)))
        .toList();
  }

  public List<LlmToolCall> restoreToolCalls(List<LlmToolCall> toolCalls, RedactionSession session) {
    if (toolCalls == null || toolCalls.isEmpty()) {
      return List.of();
    }
    return toolCalls.stream()
        .map(toolCall -> toolCall.withArgumentsJson(restoreJson(toolCall.argumentsJson(), session)))
        .toList();
  }

  public String redactJson(String json, RedactionSession session) {
    if (!properties.isEnabled()) {
      return json;
    }
    return mapJsonTextNodes(json, text -> redact(text, session));
  }

  public String restoreJson(String json, RedactionSession session) {
    if (session == null || session.isEmpty()) {
      return json;
    }
    return mapJsonTextNodes(json, session::restore);
  }

  private String mapJsonTextNodes(String json, UnaryOperator<String> mapper) {
    if (json == null || json.isBlank()) {
      return json;
    }
    JsonNode root;
    try {
      root = objectMapper.readTree(json);
    } catch (JsonProcessingException e) {
      log.warn("Tool call arguments are not valid JSON, passing them through untouched");
      return json;
    }
    JsonNode mapped = mapNode(root, mapper);
    try {
      return objectMapper.writeValueAsString(mapped);
    } catch (JsonProcessingException e) {
      log.warn("Failed to serialize mapped tool call arguments, passing them through untouched");
      return json;
    }
  }

  private JsonNode mapNode(JsonNode node, UnaryOperator<String> mapper) {
    if (node.isTextual()) {
      return TextNode.valueOf(mapper.apply(node.textValue()));
    }
    if (node.isArray()) {
      ArrayNode array = objectMapper.createArrayNode();
      node.forEach(element -> array.add(mapNode(element, mapper)));
      return array;
    }
    if (node.isObject()) {
      ObjectNode object = objectMapper.createObjectNode();
      node.properties()
          .forEach(
              (Map.Entry<String, JsonNode> entry) ->
                  object.set(entry.getKey(), mapNode(entry.getValue(), mapper)));
      return object;
    }
    return node;
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
    StringBuilder result = new StringBuilder(text.length());
    do {
      matcher.appendReplacement(
          result, Matcher.quoteReplacement(session.placeholderFor(pii.label(), matcher.group())));
    } while (matcher.find());
    matcher.appendTail(result);
    return result.toString();
  }
}
