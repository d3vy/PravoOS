package com.pravoos.ai.core.internal.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pravoos.ai.core.api.AiActorRole;
import com.pravoos.ai.core.api.AiTool;
import com.pravoos.ai.core.api.AiToolContext;
import com.pravoos.ai.core.api.AiToolResult;
import com.pravoos.ai.llm.api.LlmClient;
import com.pravoos.ai.llm.api.LlmMessage;
import com.pravoos.ai.llm.api.LlmOptions;
import com.pravoos.ai.llm.api.LlmResult;
import com.pravoos.ai.llm.api.LlmStreamResult;
import com.pravoos.ai.llm.api.LlmToolCall;
import com.pravoos.ai.llm.api.LlmUsage;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AgentLoopTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final AiToolContext CONTEXT =
      new AiToolContext(UUID.randomUUID(), List.of(UUID.randomUUID()), AiActorRole.LAWYER);
  private static final String USER_MESSAGE = "Что по делу?";

  @Mock private LlmClient llmClient;

  private final List<String> tokens = new ArrayList<>();
  private final List<ToolStep> steps = new ArrayList<>();

  private AgentLoop loopWith(AgentProperties properties, AiTool... tools) {
    return new AgentLoop(
        llmClient,
        new AiToolRegistry(List.of(tools)),
        properties,
        MAPPER,
        new AgentMetrics(new SimpleMeterRegistry()));
  }

  private AgentLoop loopWith(AiTool... tools) {
    return loopWith(
        new AgentProperties(
            8, 12, 8000, 8000, Duration.ofSeconds(120), 120_000L, Duration.ofMinutes(30), 30),
        tools);
  }

  private AgentResult run(AgentLoop loop) {
    return loop.run("system", List.of(), USER_MESSAGE, CONTEXT, tokens::add, steps::add);
  }

  @Test
  void withoutToolsFallsBackToPlainStreamingCall() {
    AgentLoop loop = loopWith();
    when(llmClient.streamComplete(anyString(), anyList(), anyString(), any(), any()))
        .thenAnswer(
            emitting("готово", new LlmStreamResult(new LlmUsage(1, 2, 3), List.of(), null)));

    AgentResult result = run(loop);

    assertThat(result.iterations()).isEqualTo(1);
    assertThat(result.steps()).isEmpty();
    assertThat(result.usage().totalTokens()).isEqualTo(3);
    assertThat(tokens).containsExactly("готово");

    ArgumentCaptor<LlmOptions> options = ArgumentCaptor.forClass(LlmOptions.class);
    verify(llmClient).streamComplete(anyString(), anyList(), anyString(), options.capture(), any());
    assertThat(options.getValue().hasTools()).isFalse();
  }

  @Test
  void answerWithoutToolCallsStopsAfterOneIteration() {
    AgentLoop loop = loopWith(new StubTool("get_case", AiToolResult.ok("{\"id\":1}")));
    when(llmClient.streamComplete(anyString(), anyList(), anyString(), any(), any()))
        .thenAnswer(emitting("ответ", LlmStreamResult.EMPTY));

    AgentResult result = run(loop);

    assertThat(result.iterations()).isEqualTo(1);
    assertThat(result.steps()).isEmpty();
    verify(llmClient, times(1)).streamComplete(anyString(), anyList(), anyString(), any(), any());
  }

  @Test
  void executesToolCallAndFeedsResultBackAsToolMessage() {
    StubTool tool = new StubTool("get_case", AiToolResult.ok("{\"number\":\"A-1\"}"));
    AgentLoop loop = loopWith(tool);
    when(llmClient.streamComplete(anyString(), anyList(), anyString(), any(), any()))
        .thenAnswer(
            emitting(null, toolCalls(new LlmToolCall("call-1", "get_case", "{\"caseId\":\"x\"}"))))
        .thenAnswer(
            emitting("дело A-1", new LlmStreamResult(new LlmUsage(4, 5, 9), List.of(), null)));

    AgentResult result = run(loop);

    assertThat(result.iterations()).isEqualTo(2);
    assertThat(result.steps())
        .extracting(ToolStep::name, ToolStep::status)
        .containsExactly(org.assertj.core.groups.Tuple.tuple("get_case", ToolStepStatus.OK));
    assertThat(tool.invocations).isEqualTo(1);

    ArgumentCaptor<List<LlmMessage>> history = historyCaptor();
    ArgumentCaptor<String> userMessage = ArgumentCaptor.forClass(String.class);
    verify(llmClient, times(2))
        .streamComplete(anyString(), history.capture(), userMessage.capture(), any(), any());

    assertThat(userMessage.getAllValues().get(0)).isEqualTo(USER_MESSAGE);
    assertThat(userMessage.getAllValues().get(1)).isBlank();

    List<LlmMessage> secondCall = history.getAllValues().get(1);
    assertThat(secondCall)
        .extracting(LlmMessage::role)
        .containsExactly(LlmMessage.ROLE_USER, LlmMessage.ROLE_ASSISTANT, LlmMessage.ROLE_TOOL);
    assertThat(secondCall.get(1).toolCalls()).extracting(LlmToolCall::id).containsExactly("call-1");
    LlmMessage toolMessage = secondCall.get(2);
    assertThat(toolMessage.toolCallId()).isEqualTo("call-1");
    assertThat(toolMessage.content()).contains("A-1").contains("РЕЗУЛЬТАТ_ИНСТРУМЕНТА");
  }

  @Test
  void sendsToolChoiceNoneOnLastAllowedIteration() {
    AgentLoop loop =
        loopWith(
            new AgentProperties(
                2, 12, 8000, 8000, Duration.ofSeconds(120), 120_000L, Duration.ofMinutes(30), 30),
            new StubTool("get_case", AiToolResult.ok("{}")));
    when(llmClient.streamComplete(anyString(), anyList(), anyString(), any(), any()))
        .thenAnswer(emitting(null, toolCalls(new LlmToolCall("c1", "get_case", "{}"))))
        .thenAnswer(emitting("финал", LlmStreamResult.EMPTY));

    AgentResult result = run(loop);

    assertThat(result.iterations()).isEqualTo(2);
    ArgumentCaptor<LlmOptions> options = ArgumentCaptor.forClass(LlmOptions.class);
    verify(llmClient, times(2))
        .streamComplete(anyString(), anyList(), anyString(), options.capture(), any());
    assertThat(options.getAllValues().get(0).toolChoice()).isEqualTo(LlmOptions.TOOL_CHOICE_AUTO);
    assertThat(options.getAllValues().get(1).toolChoice()).isEqualTo(LlmOptions.TOOL_CHOICE_NONE);
  }

  @Test
  void deduplicatesIdenticalToolCalls() {
    StubTool tool = new StubTool("get_case", AiToolResult.ok("{\"number\":\"A-1\"}"));
    AgentLoop loop = loopWith(tool);
    when(llmClient.streamComplete(anyString(), anyList(), anyString(), any(), any()))
        .thenAnswer(
            emitting(null, toolCalls(new LlmToolCall("c1", "get_case", "{\"caseId\":\"x\"}"))))
        .thenAnswer(
            emitting(null, toolCalls(new LlmToolCall("c2", "get_case", "{\"caseId\":\"x\"}"))))
        .thenAnswer(emitting("ответ", LlmStreamResult.EMPTY));

    AgentResult result = run(loop);

    assertThat(tool.invocations).isEqualTo(1);
    assertThat(result.steps())
        .extracting(ToolStep::status)
        .containsExactly(ToolStepStatus.OK, ToolStepStatus.SKIPPED);
  }

  @Test
  void unknownToolBecomesErrorStepInsteadOfException() {
    AgentLoop loop = loopWith(new StubTool("get_case", AiToolResult.ok("{}")));
    when(llmClient.streamComplete(anyString(), anyList(), anyString(), any(), any()))
        .thenAnswer(emitting(null, toolCalls(new LlmToolCall("c1", "delete_everything", "{}"))))
        .thenAnswer(emitting("ответ", LlmStreamResult.EMPTY));

    AgentResult result = run(loop);

    assertThat(result.steps())
        .singleElement()
        .satisfies(
            step -> {
              assertThat(step.status()).isEqualTo(ToolStepStatus.ERROR);
              assertThat(step.resultPreview()).contains("недоступен");
            });
  }

  @Test
  void toolFailureIsReportedToModelAndLoopContinues() {
    AiTool failing =
        new StubTool("get_case", null) {
          @Override
          public AiToolResult execute(JsonNode arguments, AiToolContext context) {
            throw new IllegalStateException("boom");
          }
        };
    AgentLoop loop = loopWith(failing);
    when(llmClient.streamComplete(anyString(), anyList(), anyString(), any(), any()))
        .thenAnswer(emitting(null, toolCalls(new LlmToolCall("c1", "get_case", "{}"))))
        .thenAnswer(emitting("не смог", LlmStreamResult.EMPTY));

    AgentResult result = run(loop);

    assertThat(result.iterations()).isEqualTo(2);
    assertThat(result.steps())
        .singleElement()
        .extracting(ToolStep::status)
        .isEqualTo(ToolStepStatus.ERROR);
  }

  @Test
  void toolReturningErrorResultIsReportedWithoutThrowing() {
    AgentLoop loop = loopWith(new StubTool("get_case", AiToolResult.error("Дело не найдено")));
    when(llmClient.streamComplete(anyString(), anyList(), anyString(), any(), any()))
        .thenAnswer(emitting(null, toolCalls(new LlmToolCall("c1", "get_case", "{}"))))
        .thenAnswer(emitting("ответ", LlmStreamResult.EMPTY));

    AgentResult result = run(loop);

    assertThat(result.steps())
        .singleElement()
        .satisfies(
            step -> {
              assertThat(step.status()).isEqualTo(ToolStepStatus.ERROR);
              assertThat(step.resultPreview()).isEqualTo("Дело не найдено");
            });
  }

  @Test
  void invalidArgumentsJsonNeverReachesTool() {
    StubTool tool = new StubTool("get_case", AiToolResult.ok("{}"));
    AgentLoop loop = loopWith(tool);
    when(llmClient.streamComplete(anyString(), anyList(), anyString(), any(), any()))
        .thenAnswer(emitting(null, toolCalls(new LlmToolCall("c1", "get_case", "{not json"))))
        .thenAnswer(emitting("ответ", LlmStreamResult.EMPTY));

    AgentResult result = run(loop);

    assertThat(tool.invocations).isZero();
    assertThat(result.steps())
        .singleElement()
        .extracting(ToolStep::status)
        .isEqualTo(ToolStepStatus.ERROR);
  }

  @Test
  void truncatesOversizedToolResult() {
    String huge = "x".repeat(200);
    AgentLoop loop =
        loopWith(
            new AgentProperties(
                8, 12, 50, 8000, Duration.ofSeconds(120), 120_000L, Duration.ofMinutes(30), 30),
            new StubTool("get_case", AiToolResult.ok(huge)));
    when(llmClient.streamComplete(anyString(), anyList(), anyString(), any(), any()))
        .thenAnswer(emitting(null, toolCalls(new LlmToolCall("c1", "get_case", "{}"))))
        .thenAnswer(emitting("ответ", LlmStreamResult.EMPTY));

    run(loop);

    ArgumentCaptor<List<LlmMessage>> history = historyCaptor();
    verify(llmClient, times(2))
        .streamComplete(anyString(), history.capture(), anyString(), any(), any());
    String toolContent = history.getAllValues().get(1).get(2).content();
    assertThat(toolContent).contains("…");
    assertThat(toolContent.length()).isLessThan(150);
  }

  @Test
  void rejectsOversizedArguments() {
    StubTool tool = new StubTool("get_case", AiToolResult.ok("{}"));
    AgentLoop loop =
        loopWith(
            new AgentProperties(
                8, 12, 8000, 10, Duration.ofSeconds(120), 120_000L, Duration.ofMinutes(30), 30),
            tool);
    when(llmClient.streamComplete(anyString(), anyList(), anyString(), any(), any()))
        .thenAnswer(
            emitting(
                null,
                toolCalls(
                    new LlmToolCall("c1", "get_case", "{\"caseId\":\"" + "x".repeat(50) + "\"}"))))
        .thenAnswer(emitting("ответ", LlmStreamResult.EMPTY));

    AgentResult result = run(loop);

    assertThat(tool.invocations).isZero();
    assertThat(result.steps())
        .singleElement()
        .extracting(ToolStep::status)
        .isEqualTo(ToolStepStatus.ERROR);
  }

  @Test
  void stopsExecutingToolsOnceCallBudgetIsSpent() {
    StubTool tool = new StubTool("get_case", AiToolResult.ok("{}"));
    AgentLoop loop =
        loopWith(
            new AgentProperties(
                8, 1, 8000, 8000, Duration.ofSeconds(120), 120_000L, Duration.ofMinutes(30), 30),
            tool);
    when(llmClient.streamComplete(anyString(), anyList(), anyString(), any(), any()))
        .thenAnswer(
            emitting(
                null,
                toolCalls(
                    new LlmToolCall("c1", "get_case", "{\"caseId\":\"a\"}"),
                    new LlmToolCall("c2", "get_case", "{\"caseId\":\"b\"}"))))
        .thenAnswer(emitting("ответ", LlmStreamResult.EMPTY));

    AgentResult result = run(loop);

    assertThat(tool.invocations).isEqualTo(1);
    assertThat(result.steps())
        .extracting(ToolStep::status)
        .containsExactly(ToolStepStatus.OK, ToolStepStatus.SKIPPED);
  }

  @Test
  void accumulatesUsageAcrossIterations() {
    AgentLoop loop = loopWith(new StubTool("get_case", AiToolResult.ok("{}")));
    when(llmClient.streamComplete(anyString(), anyList(), anyString(), any(), any()))
        .thenAnswer(
            emitting(
                null,
                new LlmStreamResult(
                    new LlmUsage(10, 1, 11),
                    List.of(new LlmToolCall("c1", "get_case", "{}")),
                    "tool_calls")))
        .thenAnswer(
            emitting("ответ", new LlmStreamResult(new LlmUsage(20, 2, 22), List.of(), null)));

    AgentResult result = run(loop);

    assertThat(result.usage().promptTokens()).isEqualTo(30);
    assertThat(result.usage().completionTokens()).isEqualTo(3);
    assertThat(result.usage().totalTokens()).isEqualTo(33);
  }

  @Test
  void emitsRunningAndFinishedStepsToConsumer() {
    AgentLoop loop = loopWith(new StubTool("get_case", AiToolResult.ok("{}")));
    when(llmClient.streamComplete(anyString(), anyList(), anyString(), any(), any()))
        .thenAnswer(emitting(null, toolCalls(new LlmToolCall("c1", "get_case", "{}"))))
        .thenAnswer(emitting("ответ", LlmStreamResult.EMPTY));

    run(loop);

    assertThat(steps)
        .extracting(ToolStep::status)
        .containsExactly(ToolStepStatus.RUNNING, ToolStepStatus.OK);
  }

  @Test
  void neverCallsToolWhenModelReturnsNoToolCalls() {
    StubTool tool = new StubTool("get_case", AiToolResult.ok("{}"));
    AgentLoop loop = loopWith(tool);
    when(llmClient.streamComplete(anyString(), anyList(), anyString(), any(), any()))
        .thenAnswer(emitting("ответ", LlmStreamResult.EMPTY));

    run(loop);

    assertThat(tool.invocations).isZero();
    verify(llmClient, never()).complete(anyString(), anyList(), anyString());
  }

  @Test
  void nonStreamingModeRunsTheSameLoopAndReturnsFinalContent() {
    StubTool tool = new StubTool("get_case", AiToolResult.ok("{\"number\":\"A-1\"}"));
    AgentLoop loop = loopWith(tool);
    when(llmClient.complete(anyString(), anyList(), anyString(), any()))
        .thenReturn(
            new LlmResult(
                null,
                new LlmUsage(1, 1, 2),
                List.of(new LlmToolCall("c1", "get_case", "{}")),
                "tool_calls"))
        .thenReturn(new LlmResult("дело A-1", new LlmUsage(3, 1, 4)));

    AgentResult result = loop.run("system", List.of(), USER_MESSAGE, CONTEXT, steps::add);

    assertThat(result.content()).isEqualTo("дело A-1");
    assertThat(result.iterations()).isEqualTo(2);
    assertThat(tool.invocations).isEqualTo(1);
    assertThat(result.usage().totalTokens()).isEqualTo(6);
    verify(llmClient, never()).streamComplete(anyString(), anyList(), anyString(), any(), any());
  }

  @Test
  void nonStreamingModeWithoutToolsFallsBackToPlainCompletion() {
    AgentLoop loop = loopWith();
    when(llmClient.complete(anyString(), anyList(), anyString(), any()))
        .thenReturn(new LlmResult("готово", new LlmUsage(1, 2, 3)));

    AgentResult result = loop.run("system", List.of(), USER_MESSAGE, CONTEXT, steps::add);

    assertThat(result.content()).isEqualTo("готово");
    assertThat(result.iterations()).isEqualTo(1);
    ArgumentCaptor<LlmOptions> options = ArgumentCaptor.forClass(LlmOptions.class);
    verify(llmClient).complete(anyString(), anyList(), anyString(), options.capture());
    assertThat(options.getValue().hasTools()).isFalse();
  }

  @Test
  void doesNotReuseAFailedToolResultForALaterIdenticalCall() {
    FlakyTool tool = new FlakyTool("get_case");
    AgentLoop loop = loopWith(tool);
    when(llmClient.streamComplete(anyString(), anyList(), anyString(), any(), any()))
        .thenAnswer(emitting(null, toolCalls(new LlmToolCall("c1", "get_case", "{}"))))
        .thenAnswer(emitting(null, toolCalls(new LlmToolCall("c2", "get_case", "{}"))))
        .thenAnswer(emitting("ответ", LlmStreamResult.EMPTY));

    AgentResult result =
        loop.run("system", List.of(), USER_MESSAGE, CONTEXT, tokens::add, steps::add);

    assertThat(tool.invocations).isEqualTo(2);
    assertThat(result.steps())
        .extracting(ToolStep::status)
        .containsExactly(ToolStepStatus.ERROR, ToolStepStatus.OK);
  }

  @Test
  void doesNotDeduplicateWriteToolCalls() {
    WriteTool tool = new WriteTool("create_client");
    AgentLoop loop = loopWith(tool);
    when(llmClient.streamComplete(anyString(), anyList(), anyString(), any(), any()))
        .thenAnswer(
            emitting(
                null,
                toolCalls(
                    new LlmToolCall("c1", "create_client", "{\"name\":\"Иванов\"}"),
                    new LlmToolCall("c2", "create_client", "{\"name\":\"Иванов\"}"))))
        .thenAnswer(emitting("готово", LlmStreamResult.EMPTY));

    AgentResult result =
        loop.run("system", List.of(), USER_MESSAGE, CONTEXT, tokens::add, steps::add);

    assertThat(tool.invocations).isEqualTo(2);
    assertThat(result.steps())
        .extracting(ToolStep::status)
        .containsExactly(ToolStepStatus.OK, ToolStepStatus.OK);
  }

  @Test
  void stopsRequestingToolsOnceTheTokenBudgetIsSpent() {
    StubTool tool = new StubTool("get_case", AiToolResult.ok("{}"));
    AgentLoop loop =
        loopWith(
            new AgentProperties(
                8, 12, 8000, 8000, Duration.ofSeconds(120), 15L, Duration.ofMinutes(30), 30),
            tool);
    when(llmClient.streamComplete(anyString(), anyList(), anyString(), any(), any()))
        .thenAnswer(
            emitting(
                null,
                new LlmStreamResult(
                    new LlmUsage(10, 10, 20),
                    List.of(new LlmToolCall("c1", "get_case", "{}")),
                    "tool_calls")))
        .thenAnswer(emitting("финал", LlmStreamResult.EMPTY));

    AgentResult result = run(loop);

    assertThat(result.iterations()).isEqualTo(2);
    ArgumentCaptor<LlmOptions> options = ArgumentCaptor.forClass(LlmOptions.class);
    verify(llmClient, times(2))
        .streamComplete(anyString(), anyList(), anyString(), options.capture(), any());
    assertThat(options.getAllValues().get(1).toolChoice()).isEqualTo(LlmOptions.TOOL_CHOICE_NONE);
  }

  @Test
  void stopsRequestingToolsOnceTheTimeBudgetIsSpent() {
    StubTool tool = new StubTool("get_case", AiToolResult.ok("{}"));
    AgentLoop loop =
        loopWith(
            new AgentProperties(
                8, 12, 8000, 8000, Duration.ofNanos(1L), 120_000L, Duration.ofMinutes(30), 30),
            tool);
    when(llmClient.streamComplete(anyString(), anyList(), anyString(), any(), any()))
        .thenAnswer(emitting("сразу финал", LlmStreamResult.EMPTY));

    AgentResult result = run(loop);

    assertThat(result.iterations()).isEqualTo(1);
    assertThat(tool.invocations).isZero();
    ArgumentCaptor<LlmOptions> options = ArgumentCaptor.forClass(LlmOptions.class);
    verify(llmClient).streamComplete(anyString(), anyList(), anyString(), options.capture(), any());
    assertThat(options.getValue().toolChoice()).isEqualTo(LlmOptions.TOOL_CHOICE_NONE);
  }

  @Test
  void reportsUsageAfterEveryModelTurnInsteadOfOnlyAtTheEnd() {
    StubTool tool = new StubTool("get_case", AiToolResult.ok("{}"));
    AgentLoop loop = loopWith(tool);
    when(llmClient.streamComplete(anyString(), anyList(), anyString(), any(), any()))
        .thenAnswer(
            emitting(
                null,
                new LlmStreamResult(
                    new LlmUsage(10, 5, 15),
                    List.of(new LlmToolCall("c1", "get_case", "{}")),
                    "tool_calls")))
        .thenAnswer(
            emitting("финал", new LlmStreamResult(new LlmUsage(20, 5, 25), List.of(), null)));

    List<Integer> reportedUsage = new ArrayList<>();
    AgentResult result =
        loop.run(
            "system",
            List.of(),
            USER_MESSAGE,
            CONTEXT,
            tokens::add,
            steps::add,
            usage -> reportedUsage.add(usage.totalTokens()));

    assertThat(reportedUsage).containsExactly(15, 25);
    assertThat(result.usage().totalTokens()).isEqualTo(40);
  }

  @Test
  void usageOfSpentTurnsIsReportedEvenWhenTheClientDropsTheStream() {
    StubTool tool = new StubTool("get_case", AiToolResult.ok("{}"));
    AgentLoop loop = loopWith(tool);
    when(llmClient.streamComplete(anyString(), anyList(), anyString(), any(), any()))
        .thenAnswer(
            emitting(
                null,
                new LlmStreamResult(
                    new LlmUsage(10, 5, 15),
                    List.of(new LlmToolCall("c1", "get_case", "{}")),
                    "tool_calls")));

    List<Integer> reportedUsage = new ArrayList<>();

    assertThatThrownBy(
            () ->
                loop.run(
                    "system",
                    List.of(),
                    USER_MESSAGE,
                    CONTEXT,
                    tokens::add,
                    step -> {
                      throw new IllegalStateException("client disconnected");
                    },
                    usage -> reportedUsage.add(usage.totalTokens())))
        .isInstanceOf(IllegalStateException.class);
    assertThat(reportedUsage).containsExactly(15);
  }

  private static LlmStreamResult toolCalls(LlmToolCall... calls) {
    return new LlmStreamResult(LlmUsage.EMPTY, List.of(calls), "tool_calls");
  }

  @SuppressWarnings("unchecked")
  private static ArgumentCaptor<List<LlmMessage>> historyCaptor() {
    return ArgumentCaptor.forClass(List.class);
  }

  @SuppressWarnings("unchecked")
  private static org.mockito.stubbing.Answer<LlmStreamResult> emitting(
      String token, LlmStreamResult result) {
    return invocation -> {
      if (token != null) {
        ((Consumer<String>) invocation.getArgument(4)).accept(token);
      }
      return result;
    };
  }

  private static class StubTool implements AiTool {

    private final String name;
    private final AiToolResult result;
    protected int invocations;

    private StubTool(String name, AiToolResult result) {
      this.name = name;
      this.result = result;
    }

    @Override
    public String name() {
      return name;
    }

    @Override
    public String description() {
      return "stub";
    }

    @Override
    public JsonNode parameters() {
      return MAPPER.createObjectNode().put("type", "object");
    }

    @Override
    public AiToolResult execute(JsonNode arguments, AiToolContext context) {
      invocations++;
      return result;
    }
  }

  private static final class FlakyTool extends StubTool {

    private FlakyTool(String name) {
      super(name, null);
    }

    @Override
    public AiToolResult execute(JsonNode arguments, AiToolContext context) {
      invocations++;
      return invocations == 1 ? AiToolResult.error("временная ошибка") : AiToolResult.ok("{}");
    }
  }

  private static final class WriteTool extends StubTool {

    private WriteTool(String name) {
      super(name, AiToolResult.ok("{}"));
    }

    @Override
    public boolean readOnly() {
      return false;
    }
  }
}
