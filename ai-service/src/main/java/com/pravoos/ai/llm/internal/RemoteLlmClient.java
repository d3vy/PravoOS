package com.pravoos.ai.llm.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pravoos.ai.llm.api.EmbeddingResult;
import com.pravoos.ai.llm.api.LlmClient;
import com.pravoos.ai.llm.api.LlmMessage;
import com.pravoos.ai.llm.api.LlmOptions;
import com.pravoos.ai.llm.api.LlmResult;
import com.pravoos.ai.llm.api.LlmStreamResult;
import com.pravoos.ai.llm.api.LlmToolCall;
import com.pravoos.ai.llm.api.LlmUsage;
import com.pravoos.ai.shared.config.LlmServiceProperties;
import com.pravoos.ai.shared.exception.LlmException;
import com.pravoos.ai.shared.security.AiProcessingGuard;
import com.pravoos.cloud.DiscoveryAwareRestClients;
import com.pravoos.common.security.internal.InternalCallerHeaders;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class RemoteLlmClient implements LlmClient {

  private static final Logger log = LoggerFactory.getLogger(RemoteLlmClient.class);
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
  private static final Duration READ_TIMEOUT = Duration.ofSeconds(180);
  private static final Duration GUARD_READ_TIMEOUT = Duration.ofSeconds(12);
  private static final String CALLER_NAME = "ai-service";

  private final RestClient restClient;
  private final RestClient guardRestClient;
  private final ObjectMapper objectMapper;
  private final AiProcessingGuard aiProcessingGuard;

  public RemoteLlmClient(
      LlmServiceProperties properties,
      @LoadBalanced RestClient.Builder loadBalancedRestClientBuilder,
      ObjectMapper objectMapper,
      AiProcessingGuard aiProcessingGuard) {
    this.restClient =
        buildClient(
            properties.baseUrl(),
            READ_TIMEOUT,
            properties.internalSecret(),
            loadBalancedRestClientBuilder);
    this.guardRestClient =
        buildClient(
            properties.baseUrl(),
            GUARD_READ_TIMEOUT,
            properties.internalSecret(),
            loadBalancedRestClientBuilder);
    this.objectMapper = objectMapper;
    this.aiProcessingGuard = aiProcessingGuard;
  }

  private static RestClient buildClient(
      String baseUrl,
      Duration readTimeout,
      String internalSecret,
      RestClient.Builder loadBalancedBuilder) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
    requestFactory.setReadTimeout(readTimeout);
    return DiscoveryAwareRestClients.builderFor(baseUrl, loadBalancedBuilder)
        .baseUrl(baseUrl)
        .defaultHeader(InternalCallerHeaders.CALLER, CALLER_NAME)
        .defaultHeader(InternalCallerHeaders.SECRET, internalSecret)
        .requestFactory(requestFactory)
        .build();
  }

  @Override
  public LlmResult complete(String systemPrompt, List<LlmMessage> history, String userMessage) {
    return complete(systemPrompt, history, userMessage, LlmOptions.DEFAULT);
  }

  @Override
  public LlmResult complete(
      String systemPrompt, List<LlmMessage> history, String userMessage, LlmOptions options) {
    aiProcessingGuard.ensureRemoteCallAllowed();
    try {
      LlmResult result =
          clientFor(options)
              .post()
              .uri("/internal/llm/complete")
              .contentType(MediaType.APPLICATION_JSON)
              .body(new CompleteRequest(systemPrompt, history, userMessage, options))
              .retrieve()
              .body(LlmResult.class);
      if (result == null) {
        throw new LlmException("Empty response from llm-service");
      }
      return result;
    } catch (RestClientException e) {
      log.error("llm-service completion failed: {}", e.getMessage());
      throw new LlmException("llm-service completion failed: " + e.getMessage());
    }
  }

  @Override
  public LlmStreamResult streamComplete(
      String systemPrompt,
      List<LlmMessage> history,
      String userMessage,
      LlmOptions options,
      Consumer<String> tokenConsumer) {
    aiProcessingGuard.ensureRemoteCallAllowed();
    try {
      return restClient
          .post()
          .uri("/internal/llm/stream")
          .contentType(MediaType.APPLICATION_JSON)
          .accept(MediaType.TEXT_EVENT_STREAM)
          .body(
              new CompleteRequest(
                  systemPrompt,
                  history,
                  userMessage,
                  options == null ? LlmOptions.DEFAULT : options))
          .exchange(
              (clientRequest, clientResponse) -> {
                if (clientResponse.getStatusCode().isError()) {
                  throw new LlmException(
                      "llm-service stream failed with status "
                          + clientResponse.getStatusCode().value());
                }
                return consumeSse(clientResponse.getBody(), tokenConsumer);
              });
    } catch (RestClientException e) {
      log.error("llm-service stream failed: {}", e.getMessage());
      throw new LlmException("llm-service stream failed: " + e.getMessage());
    }
  }

  private LlmStreamResult consumeSse(InputStream body, Consumer<String> tokenConsumer)
      throws IOException {
    StreamAccumulator accumulator = new StreamAccumulator();
    String currentEvent = null;
    StringBuilder data = new StringBuilder();
    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        if (line.isEmpty()) {
          dispatchEvent(currentEvent, data.toString(), tokenConsumer, accumulator);
          currentEvent = null;
          data.setLength(0);
          continue;
        }
        if (line.startsWith("event:")) {
          currentEvent = line.substring("event:".length()).trim();
        } else if (line.startsWith("data:")) {
          if (data.length() > 0) {
            data.append('\n');
          }
          data.append(line.substring("data:".length()).trim());
        }
      }
      dispatchEvent(currentEvent, data.toString(), tokenConsumer, accumulator);
    }
    return accumulator.toResult();
  }

  private void dispatchEvent(
      String event, String data, Consumer<String> tokenConsumer, StreamAccumulator accumulator)
      throws IOException {
    if (event == null || data.isEmpty()) {
      return;
    }
    if ("token".equals(event)) {
      StreamToken token = objectMapper.readValue(data, StreamToken.class);
      if (token.token() != null && !token.token().isEmpty()) {
        tokenConsumer.accept(token.token());
      }
      return;
    }
    if ("tool_calls".equals(event)) {
      StreamToolCalls toolCalls = objectMapper.readValue(data, StreamToolCalls.class);
      accumulator.toolCalls =
          toolCalls.toolCalls() == null ? List.of() : List.copyOf(toolCalls.toolCalls());
      return;
    }
    if ("usage".equals(event)) {
      accumulator.usage = objectMapper.readValue(data, LlmUsage.class);
      return;
    }
    if ("error".equals(event)) {
      StreamError error = objectMapper.readValue(data, StreamError.class);
      log.error("llm-service reported a stream failure: {}", error.message());
      throw new LlmException(
          "llm-service stream failed: "
              + (error.message() == null ? "unknown error" : error.message()));
    }
  }

  private static final class StreamAccumulator {
    private LlmUsage usage = LlmUsage.EMPTY;
    private List<LlmToolCall> toolCalls = List.of();

    private LlmStreamResult toResult() {
      return new LlmStreamResult(usage, toolCalls, toolCalls.isEmpty() ? null : "tool_calls");
    }
  }

  @Override
  public float[] embed(String text) {
    aiProcessingGuard.ensureRemoteCallAllowed();
    try {
      EmbedResponse response =
          restClient
              .post()
              .uri("/internal/llm/embed")
              .contentType(MediaType.APPLICATION_JSON)
              .body(new EmbedRequest(text))
              .retrieve()
              .body(EmbedResponse.class);
      if (response == null || response.embedding() == null) {
        throw new LlmException("Empty embedding response from llm-service");
      }
      return response.embedding();
    } catch (RestClientException e) {
      log.error("llm-service embedding failed: {}", e.getMessage());
      throw new LlmException("llm-service embedding failed: " + e.getMessage());
    }
  }

  @Override
  public EmbeddingResult embedBatch(List<String> texts) {
    aiProcessingGuard.ensureRemoteCallAllowed();
    try {
      EmbeddingResult result =
          restClient
              .post()
              .uri("/internal/llm/embed-batch")
              .contentType(MediaType.APPLICATION_JSON)
              .body(new EmbedBatchRequest(texts))
              .retrieve()
              .body(EmbeddingResult.class);
      if (result == null) {
        throw new LlmException("Empty batch embedding response from llm-service");
      }
      return result;
    } catch (RestClientException e) {
      log.error("llm-service batch embedding failed: {}", e.getMessage());
      throw new LlmException("llm-service batch embedding failed: " + e.getMessage());
    }
  }

  private RestClient clientFor(LlmOptions options) {
    return options != null && LlmOptions.GUARD_PROFILE.equals(options.modelProfile())
        ? guardRestClient
        : restClient;
  }

  private record CompleteRequest(
      String systemPrompt, List<LlmMessage> history, String userMessage, LlmOptions options) {}

  private record EmbedRequest(String text) {}

  private record EmbedBatchRequest(List<String> texts) {}

  private record EmbedResponse(float[] embedding) {}

  private record StreamToken(String token) {}

  private record StreamError(String message) {}

  private record StreamToolCalls(List<LlmToolCall> toolCalls) {}
}
