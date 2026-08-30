package com.pravoos.llm.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

final class OpenAiErrorSummary {

  private static final int MAX_MESSAGE_CHARS = 200;
  private static final String UNPARSEABLE = "unparseable error body";

  private OpenAiErrorSummary() {}

  static String of(ObjectMapper objectMapper, String responseBody) {
    if (responseBody == null || responseBody.isBlank()) {
      return "empty error body";
    }
    JsonNode error;
    try {
      error = objectMapper.readTree(responseBody).path("error");
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      return UNPARSEABLE;
    }
    if (error.isMissingNode() || !error.isObject()) {
      return UNPARSEABLE;
    }
    return "type="
        + textOrUnknown(error, "type")
        + ", code="
        + textOrUnknown(error, "code")
        + ", param="
        + textOrUnknown(error, "param")
        + ", message="
        + truncate(textOrUnknown(error, "message"));
  }

  private static String textOrUnknown(JsonNode error, String field) {
    JsonNode value = error.path(field);
    return value.isTextual() ? value.asText() : "unknown";
  }

  private static String truncate(String message) {
    return message.length() <= MAX_MESSAGE_CHARS
        ? message
        : message.substring(0, MAX_MESSAGE_CHARS) + "…";
  }
}
