package com.pravoos.llm.openai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pravoos.llm.domain.LlmToolCall;
import com.pravoos.llm.exception.LlmException;
import com.pravoos.llm.openai.dto.OpenAiStreamChunk.FunctionDelta;
import com.pravoos.llm.openai.dto.OpenAiStreamChunk.ToolCallDelta;
import java.util.List;
import org.junit.jupiter.api.Test;

class ToolCallAccumulatorTest {

  private static final int MAX_ARGUMENT_CHARS = 1024;

  private final ToolCallAccumulator accumulator = new ToolCallAccumulator(MAX_ARGUMENT_CHARS);

  @Test
  void assemblesArgumentsArrivingInFragments() {
    accumulator.accept(List.of(opening(0, "call_1", "create_client")));
    accumulator.accept(List.of(arguments(0, "{\"fullName\":\"")));
    accumulator.accept(List.of(arguments(0, "Иванов Иван")));
    accumulator.accept(List.of(arguments(0, " Иванович\"}")));

    assertThat(accumulator.build())
        .containsExactly(
            new LlmToolCall("call_1", "create_client", "{\"fullName\":\"Иванов Иван Иванович\"}"));
  }

  @Test
  void keepsIdAndNameFromTheFirstFragmentOnly() {
    accumulator.accept(List.of(opening(0, "call_1", "create_case")));
    accumulator.accept(List.of(new ToolCallDelta(0, null, null, new FunctionDelta(null, "{}"))));

    List<LlmToolCall> toolCalls = accumulator.build();

    assertThat(toolCalls).hasSize(1);
    assertThat(toolCalls.get(0).id()).isEqualTo("call_1");
    assertThat(toolCalls.get(0).name()).isEqualTo("create_case");
  }

  @Test
  void concatenatesNameArrivingInFragments() {
    accumulator.accept(List.of(opening(0, "call_1", "create_")));
    accumulator.accept(
        List.of(new ToolCallDelta(0, null, null, new FunctionDelta("client", "{}"))));

    assertThat(accumulator.build().get(0).name()).isEqualTo("create_client");
  }

  @Test
  void correlatesParallelToolCallsByIndexWhenFragmentsInterleave() {
    accumulator.accept(List.of(opening(0, "call_1", "create_client")));
    accumulator.accept(List.of(opening(1, "call_2", "create_case")));
    accumulator.accept(List.of(arguments(1, "{\"title\":")));
    accumulator.accept(List.of(arguments(0, "{\"fullName\":\"Пётр\"}")));
    accumulator.accept(List.of(arguments(1, "\"Иск\"}")));

    assertThat(accumulator.build())
        .containsExactly(
            new LlmToolCall("call_1", "create_client", "{\"fullName\":\"Пётр\"}"),
            new LlmToolCall("call_2", "create_case", "{\"title\":\"Иск\"}"));
  }

  @Test
  void ordersByIndexEvenWhenFragmentsArriveOutOfOrder() {
    accumulator.accept(List.of(opening(2, "call_3", "third")));
    accumulator.accept(List.of(opening(0, "call_1", "first")));
    accumulator.accept(List.of(opening(1, "call_2", "second")));

    assertThat(accumulator.build())
        .extracting(LlmToolCall::name)
        .containsExactly("first", "second", "third");
  }

  @Test
  void assemblesUnicodeEscapeSplitAcrossFragments() {
    accumulator.accept(List.of(opening(0, "call_1", "create_client")));
    accumulator.accept(List.of(arguments(0, "{\"fullName\":\"\\u04")));
    accumulator.accept(List.of(arguments(0, "18ванов\"}")));

    assertThat(accumulator.build().get(0).argumentsJson())
        .isEqualTo("{\"fullName\":\"\\u0418ванов\"}");
  }

  @Test
  void treatsMissingIndexAsTheFirstToolCall() {
    accumulator.accept(
        List.of(
            new ToolCallDelta(null, "call_1", "function", new FunctionDelta("get_case", "{}"))));

    assertThat(accumulator.build()).containsExactly(new LlmToolCall("call_1", "get_case", "{}"));
  }

  @Test
  void ignoresFragmentsWithoutAFunctionPayload() {
    accumulator.accept(List.of(new ToolCallDelta(0, "call_1", "function", null)));

    assertThat(accumulator.build()).isEmpty();
  }

  @Test
  void dropsToolCallThatNeverReceivedAnId() {
    accumulator.accept(
        List.of(new ToolCallDelta(0, null, "function", new FunctionDelta("get_case", "{}"))));

    assertThat(accumulator.build()).isEmpty();
  }

  @Test
  void ignoresNullAndEmptyDeltaBatches() {
    accumulator.accept(null);
    accumulator.accept(List.of());

    assertThat(accumulator.isEmpty()).isTrue();
    assertThat(accumulator.build()).isEmpty();
  }

  @Test
  void fallsBackToAnEmptyObjectWhenNoArgumentsArrive() {
    accumulator.accept(List.of(opening(0, "call_1", "list_cases")));

    assertThat(accumulator.build().get(0).argumentsJson()).isEqualTo("{}");
  }

  @Test
  void abortsWhenArgumentsExceedTheConfiguredLimit() {
    accumulator.accept(List.of(opening(0, "call_1", "create_client")));
    accumulator.accept(List.of(arguments(0, "x".repeat(MAX_ARGUMENT_CHARS))));

    assertThatThrownBy(() -> accumulator.accept(List.of(arguments(0, "overflow"))))
        .isInstanceOf(LlmException.class)
        .hasMessageContaining("create_client");
  }

  private static ToolCallDelta opening(int index, String id, String name) {
    return new ToolCallDelta(index, id, "function", new FunctionDelta(name, ""));
  }

  private static ToolCallDelta arguments(int index, String fragment) {
    return new ToolCallDelta(index, null, null, new FunctionDelta(null, fragment));
  }
}
