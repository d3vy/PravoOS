package com.pravoos.ai.core.api;

import com.fasterxml.jackson.databind.JsonNode;

public interface AiActionProposals {

  ProposedAction propose(String toolName, JsonNode arguments, String title, AiToolContext context);

  boolean isTrusted(String toolName, AiToolContext context);
}
