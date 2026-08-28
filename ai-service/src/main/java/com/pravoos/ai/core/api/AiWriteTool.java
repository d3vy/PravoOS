package com.pravoos.ai.core.api;

import com.fasterxml.jackson.databind.JsonNode;

public interface AiWriteTool extends AiTool {

  String title(JsonNode arguments);

  AiToolResult perform(JsonNode arguments, AiToolContext context);

  default boolean requiresApproval() {
    return true;
  }

  @Override
  default boolean readOnly() {
    return false;
  }
}
