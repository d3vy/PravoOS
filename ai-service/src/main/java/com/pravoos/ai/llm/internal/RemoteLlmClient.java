package com.pravoos.ai.llm.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pravoos.ai.llm.api.EmbeddingResult;
import com.pravoos.ai.llm.api.LlmClient;
import com.pravoos.ai.llm.api.LlmMessage;
import com.pravoos.ai.llm.api.LlmOptions;
import com.pravoos.ai.llm.api.LlmResult;
import com.pravoos.ai.llm.api.LlmUsage;
import com.pravoos.ai.shared.config.LlmServiceProperties;
import com.pravoos.ai.shared.exception.LlmException;
import com.pravoos.ai.shared.security.AiProcessingGuard;
import com.pravoos.cloud.DiscoveryAwareRestClients;
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
  private static final String SECRET_HEADER = "X-Internal-Secret";

  private final RestClient restClient;
  private final RestClient guardRestClient;
  private final String internalSecret;
  private final ObjectMapper objectMapper;
  private final AiProcessingGuard aiProcessingGuard;

  public RemoteLlmClient(
      LlmServiceProperties properties,
      @LoadBalanced RestClient.Builder loadBalancedRestClientBuilder,
      ObjectMapper objectMapper,
      AiProcessingGuard aiProcessingGuard) {
    this.restClient =
        buildClient(properties.baseUrl(), READ_TIMEOUT, loadBalancedRestClientBuilder);
    this.guardRestClient =
        buildClient(properties.baseUrl(), GUARD_READ_TIMEOUT, loadBalancedRestClientBuilder);
    this.internalSecret = properties.internalSecret();
    this.objectMapper = objectMapper;
    this.aiProcessingGuard = aiProcessingGuard;
  }

  private static RestClient buildClient(
      String baseUrl, Duration readTimeout, RestClient.Builder loadBalancedBuilder) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
    requestFactory.setReadTimeout(readTimeout);
    return DiscoveryAwareRestClients.builderFor(baseUrl, loadBalancedBuilder)
        .baseUrl(baseUrl)
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
              .header(SECRET_HEADER, internalSecret)
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
  public LlmUsage streamComplete(
      String systemPrompt,
      List<LlmMessage> history,
      String userMessage,
      Consumer<String> tokenConsumer) {
    aiProcessingGuard.ensureRemoteCallAllowed();
    try {
      return restClient
          .post()
          .uri("/internal/llm/stream")
          .header(SECRET_HEADER, internalSecret)
          .contentType(MediaType.APPLICATION_JSON)
          .accept(MediaType.TEXT_EVENT_STREAM)
          .body(new CompleteRequest(systemPrompt, history, userMessage, LlmOptions.DEFAULT))
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

  private LlmUsage consumeSse(InputStream body, Consumer<String> tokenConsumer) throws IOException {
    LlmUsage usage = LlmUsage.EMPTY;
    String currentEvent = null;
    StringBuilder data = new StringBuilder();
    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        if (line.isEmpty()) {
          LlmUsage dispatched = dispatchEvent(currentEvent, data.toString(), tokenConsumer);
          if (dispatched != null) {
            usage = dispatched;
          }
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
    }
    return usage;
  }

  private LlmUsage dispatchEvent(String event, String data, Consumer<String> tokenConsumer)
      throws IOException {
    if (event == null || data.isEmpty()) {
      return null;
    }
    if ("token".equals(event)) {
      StreamToken token = objectMapper.readValue(data, StreamToken.class);
      if (token.token() != null && !token.token().isEmpty()) {
        tokenConsumer.accept(token.token());
      }
      return null;
    }
    if ("usage".equals(event)) {
      return objectMapper.readValue(data, LlmUsage.class);
    }
    return null;
  }

  @Override
  public float[] embed(String text) {
    aiProcessingGuard.ensureRemoteCallAllowed();
    try {
      EmbedResponse response =
          restClient
              .post()
              .uri("/internal/llm/embed")
              .header(SECRET_HEADER, internalSecret)
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
              .header(SECRET_HEADER, internalSecret)
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
}
