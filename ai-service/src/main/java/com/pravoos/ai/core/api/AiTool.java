package com.pravoos.ai.core.api;

import com.fasterxml.jackson.databind.JsonNode;

public interface AiTool {

  String name();

  String description();

  JsonNode parameters();

  AiToolResult execute(JsonNode arguments, AiToolContext context);

  default boolean availableFor(AiToolContext context) {
    return true;
  }

  default boolean readOnly() {
    return true;
  }
}
