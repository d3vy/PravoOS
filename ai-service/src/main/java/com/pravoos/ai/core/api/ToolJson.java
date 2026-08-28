package com.pravoos.ai.core.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class ToolJson {

  private ToolJson() {}

  public static String write(ObjectMapper objectMapper, Object payload) {
    try {
      return objectMapper.writeValueAsString(payload);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("Failed to serialize AI tool payload", ex);
    }
  }

  public static String text(Object value) {
    return value == null ? null : value.toString();
  }

  public static String truncate(String value, int maxChars) {
    if (value == null) {
      return null;
    }
    return value.length() <= maxChars ? value : value.substring(0, maxChars) + "…";
  }
}
