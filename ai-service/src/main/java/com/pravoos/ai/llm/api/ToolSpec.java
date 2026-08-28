package com.pravoos.ai.llm.api;

import com.fasterxml.jackson.databind.JsonNode;

public record ToolSpec(String name, String description, JsonNode parameters) {}
