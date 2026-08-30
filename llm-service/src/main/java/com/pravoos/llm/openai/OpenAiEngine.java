package com.pravoos.llm.openai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pravoos.llm.config.OpenAiProperties;
import com.pravoos.llm.domain.EmbeddingResult;
import com.pravoos.llm.domain.LlmMessage;
import com.pravoos.llm.domain.LlmOptions;
import com.pravoos.llm.domain.LlmResult;
import com.pravoos.llm.domain.LlmStreamResult;
import com.pravoos.llm.domain.LlmToolCall;
import com.pravoos.llm.domain.LlmUsage;
import com.pravoos.llm.exception.LlmException;
import com.pravoos.llm.openai.dto.OpenAiChatRequest;
import com.pravoos.llm.openai.dto.OpenAiChatResponse;
import com.pravoos.llm.openai.dto.OpenAiChatStreamRequest;
import com.pravoos.llm.openai.dto.OpenAiEmbeddingRequest;
import com.pravoos.llm.openai.dto.OpenAiEmbeddingResponse;
import com.pravoos.llm.openai.dto.OpenAiMessage;
import com.pravoos.llm.openai.dto.OpenAiStreamChunk;
import com.pravoos.llm.openai.dto.OpenAiTool;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class OpenAiEngine {

  private static final Logger log = LoggerFactory.getLogger(OpenAiEngine.class);
  private static final int MAX_ATTEMPTS = 3;
  private static final long BASE_BACKOFF_MS = 500L;
  private static final long MAX_BACKOFF_MS = 8000L;
  private static final Set<Integer> RETRYABLE_STATUSES = Set.of(429, 500, 502, 503, 504);
  private static final String STREAM_DONE_MARKER = "[DONE]";

  private final RestClient restClient;
  private final OpenAiProperties properties;
  private final LlmMetrics llmMetrics;
  private final ObjectMapper objectMapper;
  private final Semaphore inFlightLimit;
  private final long acquireTimeoutMs;
  private final int maxToolArgumentChars;

  public OpenAiEngine(
      RestClient openAiRestClient,
      OpenAiProperties properties,
      LlmMetrics llmMetrics,
      ObjectMapper objectMapper,
      @Value("${llm.openai.max-concurrent-requests:20}") int maxConcurrentRequests,
      @Value("${llm.openai.acquire-timeout-ms:2000}") long acquireTimeoutMs,
      @Value("${llm.openai.max-tool-argument-chars:32768}") int maxToolArgumentChars) {
    this.restClient = openAiRestClient;
    this.properties = properties;
    this.llmMetrics = llmMetrics;
    this.objectMapper = objectMapper;
    this.inFlightLimit = new Semaphore(maxConcurrentRequests);
    this.acquireTimeoutMs = acquireTimeoutMs;
    this.maxToolArgumentChars = maxToolArgumentChars;
  }

  public LlmResult complete(
      String systemPrompt, List<LlmMessage> history, String userMessage, LlmOptions options) {
    LlmOptions resolved = LlmOptions.orDefault(options);
    List<OpenAiMessage> messages = buildMessages(systemPrompt, history, userMessage);
    OpenAiChatRequest request =
        new OpenAiChatRequest(
            resolveModel(resolved),
            messages,
            resolveMaxTokens(resolved),
            OpenAiTool.from(resolved.tools()),
            resolved.toolChoice());

    OpenAiChatResponse response =
        executeWithRetry(
            "chat completion",
            () ->
                restClient
                    .post()
                    .uri("/chat/completions")
                    .body(request)
                    .retrieve()
                    .body(OpenAiChatResponse.class));

    if (response == null) {
      throw new LlmException("Empty response from LLM API");
    }
    LlmUsage usage = response.toLlmUsage();
    llmMetrics.recordCompletion(usage);
    List<LlmToolCall> toolCalls = response.firstToolCalls();
    llmMetrics.recordToolCalls(toolCalls);
    log.debug(
        "LLM completion successful, model: {}, tokens: {}, tool calls: {}",
        request.model(),
        usage.totalTokens(),
        toolCalls.size());
    return new LlmResult(response.firstContent(), usage, toolCalls, response.firstFinishReason());
  }

  public LlmStreamResult streamComplete(
      String systemPrompt,
      List<LlmMessage> history,
      String userMessage,
      LlmOptions options,
      Consumer<String> tokenConsumer) {
    LlmOptions resolved = LlmOptions.orDefault(options);
    List<OpenAiMessage> messages = buildMessages(systemPrompt, history, userMessage);
    OpenAiChatStreamRequest request =
        OpenAiChatStreamRequest.withUsage(
            resolveModel(resolved),
            messages,
            resolveMaxTokens(resolved),
            OpenAiTool.from(resolved.tools()),
            resolved.toolChoice());

    acquireSlot("chat stream");
    try {
      LlmStreamResult result =
          restClient
              .post()
              .uri("/chat/completions")
              .body(request)
              .exchange(
                  (clientRequest, clientResponse) -> {
                    if (clientResponse.getStatusCode().isError()) {
                      log.error(
                          "OpenAI chat stream returned {}: {}",
                          clientResponse.getStatusCode().value(),
                          OpenAiErrorSummary.of(
                              objectMapper, readErrorBody(clientResponse.getBody())));
                      throw new LlmException(
                          "OpenAI chat stream failed with status "
                              + clientResponse.getStatusCode().value());
                    }
                    return consumeStream(clientResponse.getBody(), tokenConsumer);
                  });

      LlmStreamResult resolvedResult = result == null ? LlmStreamResult.EMPTY : result;
      llmMetrics.recordCompletion(resolvedResult.usage());
      llmMetrics.recordToolCalls(resolvedResult.toolCalls());
      log.debug(
          "LLM stream completed, model: {}, tokens: {}, tool calls: {}",
          request.model(),
          resolvedResult.usage().totalTokens(),
          resolvedResult.toolCalls().size());
      return resolvedResult;
    } finally {
      inFlightLimit.release();
    }
  }

  private LlmStreamResult consumeStream(java.io.InputStream body, Consumer<String> tokenConsumer)
      throws IOException {
    LlmUsage usage = LlmUsage.EMPTY;
    String finishReason = null;
    ToolCallAccumulator toolCalls = new ToolCallAccumulator(maxToolArgumentChars);
    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        if (line.isEmpty() || !line.startsWith("data:")) {
          continue;
        }
        String payload = line.substring("data:".length()).trim();
        if (payload.isEmpty()) {
          continue;
        }
        if (STREAM_DONE_MARKER.equals(payload)) {
          break;
        }
        OpenAiStreamChunk chunk = objectMapper.readValue(payload, OpenAiStreamChunk.class);
        if (chunk.usage() != null) {
          usage = chunk.toLlmUsage();
        }
        if (chunk.firstFinishReason() != null) {
          finishReason = chunk.firstFinishReason();
        }
        toolCalls.accept(chunk.firstToolCallDeltas());
        String delta = chunk.firstDelta();
        if (delta != null && !delta.isEmpty()) {
          tokenConsumer.accept(delta);
        }
      }
    }
    return new LlmStreamResult(usage, toolCalls.build(), finishReason);
  }

  private String readErrorBody(java.io.InputStream body) {
    try {
      return StreamUtils.copyToString(body, StandardCharsets.UTF_8);
    } catch (IOException e) {
      return "<unreadable body: " + e.getMessage() + ">";
    }
  }

  public float[] embed(String text) {
    return embedBatch(List.of(text)).embeddings().get(0);
  }

  public EmbeddingResult embedBatch(List<String> texts) {
    if (texts == null || texts.isEmpty()) {
      return new EmbeddingResult(List.of(), 0L);
    }
    OpenAiEmbeddingRequest request = new OpenAiEmbeddingRequest(properties.embeddingModel(), texts);

    OpenAiEmbeddingResponse response =
        executeWithRetry(
            "embedding",
            () ->
                restClient
                    .post()
                    .uri("/embeddings")
                    .body(request)
                    .retrieve()
                    .body(OpenAiEmbeddingResponse.class));

    if (response == null || response.allEmbeddings().size() != texts.size()) {
      throw new LlmException("Incomplete embedding response from API");
    }
    return new EmbeddingResult(response.allEmbeddings(), response.totalTokens());
  }

  private String resolveModel(LlmOptions options) {
    if (options.isGuardProfile()) {
      return properties.guardModel();
    }
    if (options.isRerankProfile()) {
      return properties.rerankModel();
    }
    return properties.model();
  }

  private int resolveMaxTokens(LlmOptions options) {
    return options.maxTokens() == null ? properties.maxTokens() : options.maxTokens();
  }

  private <T> T executeWithRetry(String operation, Supplier<T> call) {
    acquireSlot(operation);
    try {
      return executeWithRetryInternal(operation, call);
    } finally {
      inFlightLimit.release();
    }
  }

  private void acquireSlot(String operation) {
    boolean acquired;
    try {
      acquired = inFlightLimit.tryAcquire(acquireTimeoutMs, TimeUnit.MILLISECONDS);
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
      throw new LlmException("OpenAI " + operation + " interrupted while waiting for a slot");
    }
    if (!acquired) {
      log.warn(
          "OpenAI {} rejected: concurrency limit reached ({} in flight)",
          operation,
          inFlightLimit.availablePermits());
      throw new LlmException("OpenAI " + operation + " overloaded, please retry shortly");
    }
  }

  private <T> T executeWithRetryInternal(String operation, Supplier<T> call) {
    RestClientException lastException = null;
    for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
      try {
        return call.get();
      } catch (RestClientResponseException e) {
        lastException = e;
        int status = e.getStatusCode().value();
        if (!RETRYABLE_STATUSES.contains(status) || attempt == MAX_ATTEMPTS) {
          log.error(
              "OpenAI {} returned {}: {}",
              operation,
              status,
              OpenAiErrorSummary.of(objectMapper, e.getResponseBodyAsString()));
          throw new LlmException("OpenAI " + operation + " failed with status " + status);
        }
        log.warn(
            "OpenAI {} returned {} (attempt {}/{}), retrying",
            operation,
            status,
            attempt,
            MAX_ATTEMPTS);
        sleep(retryAfterMs(e).orElse(backoffMs(attempt)));
        continue;
      } catch (RestClientException e) {
        lastException = e;
        if (attempt == MAX_ATTEMPTS) {
          log.error("OpenAI {} failed: {}", operation, e.getMessage());
          throw new LlmException("OpenAI " + operation + " failed");
        }
        log.warn(
            "OpenAI {} failed (attempt {}/{}): {}, retrying",
            operation,
            attempt,
            MAX_ATTEMPTS,
            e.getMessage());
      }
      backoff(attempt);
    }
    log.error(
        "OpenAI {} failed after {} attempts: {}",
        operation,
        MAX_ATTEMPTS,
        lastException == null ? "unknown" : lastException.getMessage());
    throw new LlmException("OpenAI " + operation + " failed after " + MAX_ATTEMPTS + " attempts");
  }

  private void backoff(int attempt) {
    sleep(backoffMs(attempt));
  }

  private long backoffMs(int attempt) {
    long delay = Math.min(BASE_BACKOFF_MS * (1L << (attempt - 1)), MAX_BACKOFF_MS);
    return delay + ThreadLocalRandom.current().nextLong(delay / 2 + 1);
  }

  private Optional<Long> retryAfterMs(RestClientResponseException exception) {
    String header =
        exception.getResponseHeaders() == null
            ? null
            : exception.getResponseHeaders().getFirst(HttpHeaders.RETRY_AFTER);
    if (header == null || header.isBlank()) {
      return Optional.empty();
    }
    try {
      long seconds = Long.parseLong(header.trim());
      return seconds < 0
          ? Optional.empty()
          : Optional.of(Math.min(seconds * 1000L, MAX_BACKOFF_MS));
    } catch (NumberFormatException e) {
      return Optional.empty();
    }
  }

  private void sleep(long delayMs) {
    try {
      Thread.sleep(delayMs);
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
      throw new LlmException("OpenAI call interrupted during backoff");
    }
  }

  private List<OpenAiMessage> buildMessages(
      String systemPrompt, List<LlmMessage> history, String userMessage) {
    List<LlmMessage> messages = new ArrayList<>();
    messages.add(LlmMessage.system(systemPrompt));
    if (history != null) {
      messages.addAll(history);
    }
    if (userMessage != null && !userMessage.isBlank()) {
      messages.add(LlmMessage.user(userMessage));
    }
    return messages.stream().map(OpenAiMessage::from).toList();
  }
}
