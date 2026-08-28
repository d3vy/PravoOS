package com.pravoos.ai.core.internal.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pravoos.ai.core.api.AiActorRole;
import com.pravoos.ai.core.api.AiTool;
import com.pravoos.ai.core.api.AiToolContext;
import com.pravoos.ai.core.api.AiToolResult;
import com.pravoos.ai.llm.api.ToolSpec;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AiToolRegistryTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final AiToolContext CONTEXT =
      new AiToolContext(UUID.randomUUID(), List.of(UUID.randomUUID()), AiActorRole.LAWYER);

  @Test
  void rejectsDuplicateToolNamesAtStartup() {
    assertThatThrownBy(
            () ->
                new AiToolRegistry(
                    List.of(new StubTool("get_case", true), new StubTool("get_case", true))))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("get_case");
  }

  @Test
  void mapsToolsToSpecs() {
    AiToolRegistry registry = new AiToolRegistry(List.of(new StubTool("get_case", true)));

    List<ToolSpec> specs = registry.specsFor(CONTEXT);

    assertThat(specs).hasSize(1);
    assertThat(specs.getFirst().name()).isEqualTo("get_case");
    assertThat(specs.getFirst().description()).isEqualTo("description of get_case");
    assertThat(specs.getFirst().parameters().get("type").asText()).isEqualTo("object");
  }

  @Test
  void hidesToolsUnavailableForContext() {
    AiToolRegistry registry =
        new AiToolRegistry(
            List.of(new StubTool("get_case", false), new StubTool("get_client", true)));

    assertThat(registry.specsFor(CONTEXT)).extracting(ToolSpec::name).containsExactly("get_client");
    assertThat(registry.find("get_case", CONTEXT)).isEmpty();
    assertThat(registry.find("get_client", CONTEXT)).isPresent();
  }

  @Test
  void returnsEmptyForUnknownTool() {
    AiToolRegistry registry = new AiToolRegistry(List.of());

    assertThat(registry.specsFor(CONTEXT)).isEmpty();
    assertThat(registry.find("nope", CONTEXT)).isEmpty();
  }

  private record StubTool(String name, boolean available) implements AiTool {

    @Override
    public String description() {
      return "description of " + name;
    }

    @Override
    public JsonNode parameters() {
      return MAPPER.createObjectNode().put("type", "object");
    }

    @Override
    public AiToolResult execute(JsonNode arguments, AiToolContext context) {
      return AiToolResult.ok("{}");
    }

    @Override
    public boolean availableFor(AiToolContext context) {
      return available;
    }
  }
}
