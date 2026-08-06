package com.pravoos.ai.llm.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pravoos.ai.llm.api.EmbeddingResult;
import com.pravoos.ai.llm.api.LlmResult;
import com.pravoos.ai.llm.api.LlmUsage;
import com.pravoos.ai.shared.config.LlmServiceProperties;
import com.pravoos.ai.shared.exception.LlmException;
import com.pravoos.ai.shared.security.AiProcessingGuard;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class RemoteLlmClientTest {

  private HttpServer server;
  private RemoteLlmClient client;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final AtomicReference<HttpExchange> lastExchange = new AtomicReference<>();

  @BeforeEach
  void setUp() throws IOException {
    server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    server.start();
    String baseUrl = "http://localhost:" + server.getAddress().getPort();
    LlmServiceProperties properties = new LlmServiceProperties(baseUrl, "internal-secret", 1536);
    client =
        new RemoteLlmClient(
            properties, RestClient.builder(), objectMapper, new AiProcessingGuard(false));
  }

  @AfterEach
  void tearDown() {
    server.stop(0);
  }

  private void respond(String path, int status, String body) {
    server.createContext(
        path,
        exchange -> {
          lastExchange.set(exchange);
          byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(status, bytes.length);
          try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
          }
        });
  }

  @Test
  void completeReturnsParsedResultAndSendsInternalSecretHeader() throws Exception {
    respond(
        "/internal/llm/complete",
        200,
        objectMapper.writeValueAsString(new LlmResult("hello", new LlmUsage(1, 2, 3))));

    LlmResult result = client.complete("system", List.of(), "hi");

    assertThat(result.content()).isEqualTo("hello");
    assertThat(result.usage().totalTokens()).isEqualTo(3);
    assertThat(lastExchange.get().getRequestHeaders().getFirst("X-Internal-Secret"))
        .isEqualTo("internal-secret");
  }

  @Test
  void completeWrapsServerErrorIntoLlmException() {
    respond("/internal/llm/complete", 500, "{}");

    assertThatThrownBy(() -> client.complete("system", List.of(), "hi"))
        .isInstanceOf(LlmException.class);
  }

  @Test
  void completeThrowsWhenServerReturnsEmptyBody() {
    respond("/internal/llm/complete", 200, "null");

    assertThatThrownBy(() -> client.complete("system", List.of(), "hi"))
        .isInstanceOf(LlmException.class)
        .hasMessageContaining("Empty response");
  }

  @Test
  void embedReturnsEmbeddingVector() throws Exception {
    respond("/internal/llm/embed", 200, "{\"embedding\":[0.1,0.2,0.3]}");

    float[] embedding = client.embed("text");

    assertThat(embedding).containsExactly(0.1f, 0.2f, 0.3f);
  }

  @Test
  void embedThrowsWhenEmbeddingFieldIsMissing() {
    respond("/internal/llm/embed", 200, "{}");

    assertThatThrownBy(() -> client.embed("text")).isInstanceOf(LlmException.class);
  }

  @Test
  void embedWrapsUpstreamErrorIntoLlmException() {
    respond("/internal/llm/embed", 503, "{}");

    assertThatThrownBy(() -> client.embed("text")).isInstanceOf(LlmException.class);
  }

  @Test
  void embedBatchReturnsAllEmbeddings() throws Exception {
    respond(
        "/internal/llm/embed-batch",
        200,
        objectMapper.writeValueAsString(
            new EmbeddingResult(List.of(new float[] {1f, 2f}, new float[] {3f, 4f}), 42)));

    EmbeddingResult result = client.embedBatch(List.of("a", "b"));

    assertThat(result.embeddings()).hasSize(2);
    assertThat(result.totalTokens()).isEqualTo(42);
  }

  @Test
  void streamCompleteDispatchesTokensAndReturnsFinalUsage() {
    String sse =
        "event: token\ndata: {\"token\":\"Hel\"}\n\n"
            + "event: token\ndata: {\"token\":\"lo\"}\n\n"
            + "event: usage\ndata: {\"promptTokens\":5,\"completionTokens\":2,\"totalTokens\":7}\n\n";
    server.createContext(
        "/internal/llm/stream",
        exchange -> {
          byte[] bytes = sse.getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
          exchange.sendResponseHeaders(200, bytes.length);
          try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
          }
        });

    List<String> tokens = new ArrayList<>();
    LlmUsage usage = client.streamComplete("system", List.of(), "hi", tokens::add);

    assertThat(tokens).containsExactly("Hel", "lo");
    assertThat(usage.totalTokens()).isEqualTo(7);
  }

  @Test
  void streamCompleteWithoutUsageEventReturnsEmptyUsage() {
    String sse = "event: token\ndata: {\"token\":\"x\"}\n\n";
    server.createContext(
        "/internal/llm/stream",
        exchange -> {
          byte[] bytes = sse.getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
          exchange.sendResponseHeaders(200, bytes.length);
          try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
          }
        });

    LlmUsage usage = client.streamComplete("system", List.of(), "hi", token -> {});

    assertThat(usage).isEqualTo(LlmUsage.EMPTY);
  }

  @Test
  void streamCompleteThrowsWhenServerReturnsErrorStatus() {
    respond("/internal/llm/stream", 500, "");

    assertThatThrownBy(() -> client.streamComplete("system", List.of(), "hi", token -> {}))
        .isInstanceOf(LlmException.class);
  }
}
