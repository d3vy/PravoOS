package com.pravoos.ai.core.internal.agent;

import com.pravoos.ai.core.api.AiTool;
import com.pravoos.ai.core.api.AiToolContext;
import com.pravoos.ai.llm.api.ToolSpec;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class AiToolRegistry {

  private final Map<String, AiTool> toolsByName;

  public AiToolRegistry(List<AiTool> tools) {
    Map<String, AiTool> registered = new LinkedHashMap<>();
    for (AiTool tool : tools) {
      AiTool previous = registered.putIfAbsent(tool.name(), tool);
      if (previous != null) {
        throw new IllegalStateException(
            "Duplicate AI tool name '"
                + tool.name()
                + "' declared by "
                + previous.getClass().getName()
                + " and "
                + tool.getClass().getName());
      }
    }
    this.toolsByName = Map.copyOf(registered);
  }

  public List<ToolSpec> specsFor(AiToolContext context) {
    return toolsByName.values().stream()
        .filter(tool -> tool.availableFor(context))
        .map(tool -> new ToolSpec(tool.name(), tool.description(), tool.parameters()))
        .toList();
  }

  public Optional<AiTool> find(String name, AiToolContext context) {
    return Optional.ofNullable(toolsByName.get(name)).filter(tool -> tool.availableFor(context));
  }
}
