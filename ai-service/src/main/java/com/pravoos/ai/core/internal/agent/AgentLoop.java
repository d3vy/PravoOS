package com.pravoos.ai.core.internal.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.pravoos.ai.llm.api.ToolSpec;
import com.pravoos.ai.shared.util.PromptFence;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class AgentLoop {

  private static final Logger log = LoggerFactory.getLogger(AgentLoop.class);

  private static final PromptFence RESULT_FENCE = new PromptFence("РЕЗУЛЬТАТ_ИНСТРУМЕНТА");
  private static final String UNKNOWN_TOOL_MESSAGE = "Инструмент недоступен.";
  private static final String INVALID_ARGUMENTS_MESSAGE =
      "Аргументы не являются корректным JSON-объектом. Повтори вызов с исправленными аргументами.";
  private static final String ARGUMENTS_TOO_LONG_MESSAGE = "Аргументы слишком длинные.";
  private static final String BUDGET_EXCEEDED_MESSAGE =
      "Достигнут лимит вызовов инструментов. Ответь пользователю тем, что уже известно.";
  private static final String TOOL_FAILED_MESSAGE = "Инструмент завершился ошибкой.";

  private final LlmClient llmClient;
  private final AiToolRegistry toolRegistry;
  private final AgentProperties agentProperties;
  private final ObjectMapper objectMapper;
  private final AgentMetrics agentMetrics;

  public AgentLoop(
      LlmClient llmClient,
      AiToolRegistry toolRegistry,
      AgentProperties agentProperties,
      ObjectMapper objectMapper,
      AgentMetrics agentMetrics) {
    this.llmClient = llmClient;
    this.toolRegistry = toolRegistry;
    this.agentProperties = agentProperties;
    this.objectMapper = objectMapper;
    this.agentMetrics = agentMetrics;
  }

  public AgentResult run(
      String systemPrompt,
      List<LlmMessage> history,
      String userMessage,
      AiToolContext context,
      Consumer<String> tokenConsumer,
      Consumer<ToolStep> stepConsumer) {
    return execute(
        history,
        userMessage,
        context,
        stepConsumer,
        (working, pendingUserMessage, options) -> {
          LlmStreamResult result =
              llmClient.streamComplete(
                  systemPrompt, working, pendingUserMessage, options, tokenConsumer);
          return new ModelTurn(null, result.usage(), result.toolCalls());
        });
  }

  public AgentResult run(
      String systemPrompt,
      List<LlmMessage> history,
      String userMessage,
      AiToolContext context,
      Consumer<ToolStep> stepConsumer) {
    return execute(
        history,
        userMessage,
        context,
        stepConsumer,
        (working, pendingUserMessage, options) -> {
          LlmResult result = llmClient.complete(systemPrompt, working, pendingUserMessage, options);
          return new ModelTurn(result.content(), result.usage(), result.toolCalls());
        });
  }

  private AgentResult execute(
      List<LlmMessage> history,
      String userMessage,
      AiToolContext context,
      Consumer<ToolStep> stepConsumer,
      ModelCaller modelCaller) {

    List<ToolSpec> specs = toolRegistry.specsFor(context);
    if (specs.isEmpty()) {
      ModelTurn turn =
          modelCaller.call(history == null ? List.of() : history, userMessage, LlmOptions.DEFAULT);
      return new AgentResult(turn.content(), turn.usage(), List.of(), 1);
    }

    List<LlmMessage> working = new ArrayList<>(history == null ? List.of() : history);
    List<ToolStep> steps = new ArrayList<>();
    Map<String, String> executedCalls = new HashMap<>();
    UsageAccumulator usage = new UsageAccumulator();
    Deadline deadline = Deadline.startingNow(agentProperties.maxDuration());

    String pendingUserMessage = userMessage;
    String content = null;
    boolean userMessageAppended = false;
    int toolCallsMade = 0;
    int iteration = 0;

    while (iteration < agentProperties.maxIterations()) {
      iteration++;
      boolean finalIteration =
          iteration == agentProperties.maxIterations()
              || toolCallsMade >= agentProperties.maxToolCalls()
              || deadline.expired()
              || usage.totalTokens() >= agentProperties.maxTotalTokens();
      String toolChoice =
          finalIteration ? LlmOptions.TOOL_CHOICE_NONE : LlmOptions.TOOL_CHOICE_AUTO;

      ModelTurn turn =
          modelCaller.call(
              List.copyOf(working),
              pendingUserMessage,
              LlmOptions.DEFAULT.andTools(specs, toolChoice));
      usage.add(turn.usage());
      content = turn.content();

      if (turn.toolCalls().isEmpty()) {
        return new AgentResult(content, usage.toUsage(), steps, iteration);
      }

      if (!userMessageAppended && userMessage != null && !userMessage.isBlank()) {
        working.add(new LlmMessage(LlmMessage.ROLE_USER, userMessage));
        userMessageAppended = true;
      }
      pendingUserMessage = "";
      working.add(LlmMessage.assistant(turn.content(), turn.toolCalls()));

      for (LlmToolCall toolCall : turn.toolCalls()) {
        boolean withinBudget = toolCallsMade < agentProperties.maxToolCalls();
        if (withinBudget) {
          toolCallsMade++;
        }
        ToolOutcome outcome =
            executeToolCall(toolCall, context, executedCalls, withinBudget, stepConsumer);
        steps.add(outcome.step());
        working.add(LlmMessage.tool(toolCall.id(), RESULT_FENCE.wrap(outcome.content())));
      }
    }

    return new AgentResult(content, usage.toUsage(), steps, iteration);
  }

  private ToolOutcome executeToolCall(
      LlmToolCall toolCall,
      AiToolContext context,
      Map<String, String> executedCalls,
      boolean withinBudget,
      Consumer<ToolStep> stepConsumer) {

    ToolStep started = ToolStep.running(toolCall.name(), toolCall.argumentsJson());
    stepConsumer.accept(started);

    if (!withinBudget) {
      return finish(started, ToolStepStatus.SKIPPED, BUDGET_EXCEEDED_MESSAGE, 0L, stepConsumer);
    }

    String key = callKey(toolCall);
    String cached = executedCalls.get(key);
    if (cached != null) {
      return finish(started, ToolStepStatus.SKIPPED, cached, 0L, stepConsumer);
    }

    long startedAt = System.nanoTime();
    ToolStepStatus status;
    String content;
    try {
      content = invoke(toolCall, context);
      status = ToolStepStatus.OK;
    } catch (ToolInvocationException ex) {
      content = ex.getMessage();
      status = ToolStepStatus.ERROR;
    } catch (RuntimeException ex) {
      log.warn("AI tool '{}' failed unexpectedly", toolCall.name(), ex);
      content = TOOL_FAILED_MESSAGE;
      status = ToolStepStatus.ERROR;
    }
    long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L;
    if (status == ToolStepStatus.OK && isDeduplicatable(toolCall, context)) {
      executedCalls.put(key, content);
    }

    return finish(started, status, content, elapsedMs, stepConsumer);
  }

  private boolean isDeduplicatable(LlmToolCall toolCall, AiToolContext context) {
    return toolRegistry.find(toolCall.name(), context).map(AiTool::readOnly).orElse(false);
  }

  private ToolOutcome finish(
      ToolStep started,
      ToolStepStatus status,
      String content,
      long elapsedMs,
      Consumer<ToolStep> stepConsumer) {
    ToolStep finished = started.finished(status, content, elapsedMs);
    agentMetrics.recordToolCall(started.name(), status);
    stepConsumer.accept(finished);
    return new ToolOutcome(finished, content);
  }

  private String invoke(LlmToolCall toolCall, AiToolContext context) {
    Optional<AiTool> tool = toolRegistry.find(toolCall.name(), context);
    if (tool.isEmpty()) {
      throw new ToolInvocationException(UNKNOWN_TOOL_MESSAGE);
    }
    JsonNode arguments = parseArguments(toolCall.argumentsJson());
    AiToolResult result = tool.get().execute(arguments, context);
    if (result == null) {
      throw new ToolInvocationException(TOOL_FAILED_MESSAGE);
    }
    String content = truncate(result.content(), agentProperties.maxResultChars());
    if (!result.ok()) {
      throw new ToolInvocationException(content);
    }
    return content;
  }

  private JsonNode parseArguments(String argumentsJson) {
    if (argumentsJson == null || argumentsJson.isBlank()) {
      return objectMapper.createObjectNode();
    }
    if (argumentsJson.length() > agentProperties.maxArgumentsChars()) {
      throw new ToolInvocationException(ARGUMENTS_TOO_LONG_MESSAGE);
    }
    try {
      JsonNode parsed = objectMapper.readTree(argumentsJson);
      if (!parsed.isObject()) {
        throw new ToolInvocationException(INVALID_ARGUMENTS_MESSAGE);
      }
      return parsed;
    } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
      throw new ToolInvocationException(INVALID_ARGUMENTS_MESSAGE);
    }
  }

  private static String truncate(String content, int maxChars) {
    if (content == null) {
      return "";
    }
    return content.length() <= maxChars ? content : content.substring(0, maxChars) + "…";
  }

  private static String callKey(LlmToolCall toolCall) {
    return toolCall.name()
        + '|'
        + (toolCall.argumentsJson() == null ? "" : toolCall.argumentsJson());
  }

  @FunctionalInterface
  private interface ModelCaller {
    ModelTurn call(List<LlmMessage> working, String pendingUserMessage, LlmOptions options);
  }

  private record ModelTurn(String content, LlmUsage usage, List<LlmToolCall> toolCalls) {

    private ModelTurn {
      toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }
  }

  private record ToolOutcome(ToolStep step, String content) {}

  private record Deadline(long expiresAtNanos) {

    private static Deadline startingNow(Duration budget) {
      return new Deadline(System.nanoTime() + budget.toNanos());
    }

    private boolean expired() {
      return System.nanoTime() - expiresAtNanos >= 0;
    }
  }

  private static final class ToolInvocationException extends RuntimeException {
    private ToolInvocationException(String message) {
      super(message);
    }
  }

  private static final class UsageAccumulator {
    private int promptTokens;
    private int completionTokens;
    private int totalTokens;

    private void add(LlmUsage usage) {
      if (usage == null) {
        return;
      }
      promptTokens += usage.promptTokens();
      completionTokens += usage.completionTokens();
      totalTokens += usage.totalTokens();
    }

    private int totalTokens() {
      return totalTokens;
    }

    private LlmUsage toUsage() {
      return new LlmUsage(promptTokens, completionTokens, totalTokens);
    }
  }
}
