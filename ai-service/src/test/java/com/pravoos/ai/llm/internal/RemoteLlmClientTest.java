package com.pravoos.ai.llm.internal;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pravoos.ai.llm.api.EmbeddingResult;
import com.pravoos.ai.llm.api.LlmMessage;
import com.pravoos.ai.llm.api.LlmOptions;
import com.pravoos.ai.llm.api.LlmResult;
import com.pravoos.ai.llm.api.LlmStreamResult;
import com.pravoos.ai.llm.api.LlmToolCall;
import com.pravoos.ai.llm.api.LlmUsage;
import com.pravoos.ai.llm.api.ToolSpec;
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
    LlmStreamResult result = client.streamComplete("system", List.of(), "hi", tokens::add);

    assertThat(tokens).containsExactly("Hel", "lo");
    assertThat(result.usage().totalTokens()).isEqualTo(7);
    assertThat(result.hasToolCalls()).isFalse();
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

    LlmStreamResult result = client.streamComplete("system", List.of(), "hi", token -> {});

    assertThat(result.usage()).isEqualTo(LlmUsage.EMPTY);
  }

  @Test
  void streamCompleteFailsLoudlyWhenUpstreamReportsAnErrorMidStream() {
    String sse =
        "event: token\ndata: {\"token\":\"Начало отв\"}\n\n"
            + "event: error\ndata: {\"message\":\"OpenAI chat stream failed with status 500\"}\n\n";
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

    assertThatThrownBy(() -> client.streamComplete("system", List.of(), "hi", tokens::add))
        .isInstanceOf(LlmException.class)
        .hasMessageContaining("status 500");
    assertThat(tokens).containsExactly("Начало отв");
  }

  @Test
  void streamCompleteThrowsWhenServerReturnsErrorStatus() {
    respond("/internal/llm/stream", 500, "");

    assertThatThrownBy(() -> client.streamComplete("system", List.of(), "hi", token -> {}))
        .isInstanceOf(LlmException.class);
  }

  @Test
  void completeSendsToolSpecsAndParsesToolCallsBack() throws Exception {
    AtomicReference<String> capturedBody = new AtomicReference<>();
    String response =
        objectMapper.writeValueAsString(
            new LlmResult(
                "",
                new LlmUsage(1, 1, 2),
                List.of(new LlmToolCall("call_1", "create_client", "{\"fullName\":\"Пётр\"}")),
                "tool_calls"));
    server.createContext(
        "/internal/llm/complete",
        exchange -> {
          capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), UTF_8));
          byte[] bytes = response.getBytes(UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, bytes.length);
          try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
          }
        });

    LlmResult result = client.complete("system", List.of(), "создай клиента", createClientTools());

    JsonNode sentTools = objectMapper.readTree(capturedBody.get()).path("options").path("tools");
    assertThat(sentTools.get(0).path("name").textValue()).isEqualTo("create_client");
    assertThat(sentTools.get(0).path("parameters").path("type").textValue()).isEqualTo("object");
    assertThat(
            objectMapper
                .readTree(capturedBody.get())
                .path("options")
                .path("toolChoice")
                .textValue())
        .isEqualTo("auto");
    assertThat(result.finishReason()).isEqualTo("tool_calls");
    assertThat(result.toolCalls())
        .containsExactly(new LlmToolCall("call_1", "create_client", "{\"fullName\":\"Пётр\"}"));
  }

  @Test
  void streamCompleteParsesTheToolCallsEvent() {
    String sse =
        "event: token\ndata: {\"token\":\"Секунду\"}\n\n"
            + "event: tool_calls\ndata: {\"toolCalls\":[{\"id\":\"call_1\","
            + "\"name\":\"create_client\",\"argumentsJson\":\"{\\\"fullName\\\":\\\"Пётр\\\"}\"}]}\n\n"
            + "event: usage\ndata: {\"promptTokens\":5,\"completionTokens\":2,\"totalTokens\":7}\n\n";
    respondStream(sse);

    List<String> tokens = new ArrayList<>();
    LlmStreamResult result =
        client.streamComplete(
            "system", List.of(), "создай клиента", createClientTools(), tokens::add);

    assertThat(tokens).containsExactly("Секунду");
    assertThat(result.usage().totalTokens()).isEqualTo(7);
    assertThat(result.finishReason()).isEqualTo("tool_calls");
    assertThat(result.toolCalls())
        .containsExactly(new LlmToolCall("call_1", "create_client", "{\"fullName\":\"Пётр\"}"));
  }

  @Test
  void streamCompleteSendsToolSpecsToLlmService() throws Exception {
    AtomicReference<String> capturedBody = new AtomicReference<>();
    byte[] bytes =
        "event: usage\ndata: {\"promptTokens\":1,\"completionTokens\":1,\"totalTokens\":2}\n\n"
            .getBytes(UTF_8);
    server.createContext(
        "/internal/llm/stream",
        exchange -> {
          capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), UTF_8));
          exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
          exchange.sendResponseHeaders(200, bytes.length);
          try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
          }
        });

    client.streamComplete("system", List.of(), "создай клиента", createClientTools(), token -> {});

    assertThat(
            objectMapper
                .readTree(capturedBody.get())
                .path("options")
                .path("tools")
                .get(0)
                .path("name")
                .textValue())
        .isEqualTo("create_client");
  }

  @Test
  void streamCompleteSendsAssistantToolCallsAndToolResultsInHistory() throws Exception {
    AtomicReference<String> capturedBody = new AtomicReference<>();
    byte[] bytes =
        "event: usage\ndata: {\"promptTokens\":1,\"completionTokens\":1,\"totalTokens\":2}\n\n"
            .getBytes(UTF_8);
    server.createContext(
        "/internal/llm/stream",
        exchange -> {
          capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), UTF_8));
          exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
          exchange.sendResponseHeaders(200, bytes.length);
          try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
          }
        });

    List<LlmMessage> history =
        List.of(
            LlmMessage.assistant(null, List.of(new LlmToolCall("call_1", "create_client", "{}"))),
            LlmMessage.tool("call_1", "{\"clientId\":\"7\"}"));

    client.streamComplete("system", history, null, LlmOptions.DEFAULT, token -> {});

    JsonNode sentHistory = objectMapper.readTree(capturedBody.get()).path("history");
    assertThat(sentHistory.get(0).path("toolCalls").get(0).path("id").textValue())
        .isEqualTo("call_1");
    assertThat(sentHistory.get(1).path("role").textValue()).isEqualTo("tool");
    assertThat(sentHistory.get(1).path("toolCallId").textValue()).isEqualTo("call_1");
  }

  private void respondStream(String sse) {
    server.createContext(
        "/internal/llm/stream",
        exchange -> {
          byte[] bytes = sse.getBytes(UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
          exchange.sendResponseHeaders(200, bytes.length);
          try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
          }
        });
  }

  private static LlmOptions createClientTools() {
    ObjectNode parameters = JsonNodeFactory.instance.objectNode();
    parameters.put("type", "object");
    parameters.putObject("properties").putObject("fullName").put("type", "string");
    return LlmOptions.withTools(
        List.of(new ToolSpec("create_client", "Создать клиента", parameters)),
        LlmOptions.TOOL_CHOICE_AUTO);
  }
}
