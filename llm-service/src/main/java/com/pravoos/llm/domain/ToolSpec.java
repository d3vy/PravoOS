package com.pravoos.llm.domain;

import com.fasterxml.jackson.databind.JsonNode;

public record ToolSpec(String name, String description, JsonNode parameters) {}
