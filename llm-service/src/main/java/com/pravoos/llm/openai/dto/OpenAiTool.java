package com.pravoos.llm.openai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.pravoos.llm.domain.ToolSpec;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OpenAiTool(String type, Function function) {

  private static final String FUNCTION_TYPE = "function";

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Function(String name, String description, JsonNode parameters) {}

  public static OpenAiTool from(ToolSpec spec) {
    return new OpenAiTool(
        FUNCTION_TYPE, new Function(spec.name(), spec.description(), spec.parameters()));
  }

  public static List<OpenAiTool> from(List<ToolSpec> specs) {
    if (specs == null || specs.isEmpty()) {
      return null;
    }
    return specs.stream().map(OpenAiTool::from).toList();
  }
}
