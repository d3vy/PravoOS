package com.pravoos.llm.openai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pravoos.llm.config.OpenAiProperties;
import com.pravoos.llm.domain.EmbeddingResult;
import com.pravoos.llm.domain.LlmMessage;
import com.pravoos.llm.domain.LlmOptions;
import com.pravoos.llm.domain.LlmResult;
import com.pravoos.llm.domain.LlmStreamResult;
import com.pravoos.llm.domain.LlmToolCall;
import com.pravoos.llm.domain.ToolSpec;
import com.pravoos.llm.exception.LlmException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.match.MockRestRequestMatchers;
import org.springframework.web.client.RestClient;

class OpenAiEngineTest {

  private static final String BASE_URL = "http://openai.test/v1";
  private static final String CHAT_URL = BASE_URL + "/chat/completions";
  private static final String EMBED_URL = BASE_URL + "/embeddings";

  private MockRestServiceServer server;
  private OpenAiEngine engine;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
    server = MockRestServiceServer.bindTo(builder).build();
    RestClient restClient = builder.build();
    OpenAiProperties properties =
        new OpenAiProperties(
            "test-key",
            BASE_URL,
            "gpt-model",
            "gpt-guard",
            "gpt-rerank",
            "embed-model",
            1536,
            2048);
    LlmMetrics metrics = new LlmMetrics(new SimpleMeterRegistry());
    engine = new OpenAiEngine(restClient, properties, metrics, new ObjectMapper(), 5, 2000L, 32768);
  }

  @Test
  void completeReturnsContentAndUsage() {
    server
        .expect(requestTo(CHAT_URL))
        .andExpect(method(org.springframework.http.HttpMethod.POST))
        .andRespond(
            withSuccess(
                "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"Ответ\"}}],"
                    + "\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":5,\"total_tokens\":15}}",
                MediaType.APPLICATION_JSON));

    LlmResult result = engine.complete("system", List.of(), "вопрос", LlmOptions.DEFAULT);

    assertThat(result.content()).isEqualTo("Ответ");
    assertThat(result.usage().totalTokens()).isEqualTo(15);
    server.verify();
  }

  @Test
  void completeUsesRerankModelForRerankProfile() {
    server
        .expect(requestTo(CHAT_URL))
        .andExpect(MockRestRequestMatchers.jsonPath("$.model").value("gpt-rerank"))
        .andRespond(
            withSuccess(
                "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"[{\\\"id\\\":1,"
                    + "\\\"score\\\":9}]\"}}],\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":1,"
                    + "\"total_tokens\":2}}",
                MediaType.APPLICATION_JSON));

    LlmResult result =
        engine.complete(
            "system", List.of(), "фрагменты", new LlmOptions(LlmOptions.RERANK_PROFILE, 400, 0.0));

    assertThat(result.content()).contains("score");
    server.verify();
  }

  @Test
  void completeDoesNotRetryNonRetryableStatus() {
    server
        .expect(ExpectedCount.once(), requestTo(CHAT_URL))
        .andRespond(
            withStatus(HttpStatus.BAD_REQUEST)
                .body("{\"error\":\"bad request\"}")
                .contentType(MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> engine.complete("system", List.of(), "вопрос", LlmOptions.DEFAULT))
        .isInstanceOf(LlmException.class)
        .hasMessageContaining("400");
    server.verify();
  }

  @Test
  void completeRetriesRetryableStatusThenSucceeds() {
    server
        .expect(ExpectedCount.once(), requestTo(CHAT_URL))
        .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
    server
        .expect(ExpectedCount.once(), requestTo(CHAT_URL))
        .andRespond(
            withSuccess(
                "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"ok\"}}],"
                    + "\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":1,\"total_tokens\":2}}",
                MediaType.APPLICATION_JSON));

    LlmResult result = engine.complete("system", List.of(), "вопрос", LlmOptions.DEFAULT);

    assertThat(result.content()).isEqualTo("ok");
    server.verify();
  }

  @Test
  void completeWaitsForTheRetryAfterHeaderInsteadOfItsOwnBackoff() {
    server
        .expect(ExpectedCount.once(), requestTo(CHAT_URL))
        .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header(HttpHeaders.RETRY_AFTER, "2"));
    server
        .expect(ExpectedCount.once(), requestTo(CHAT_URL))
        .andRespond(
            withSuccess(
                "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"ok\"}}],"
                    + "\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":1,\"total_tokens\":2}}",
                MediaType.APPLICATION_JSON));

    long startedAt = System.nanoTime();
    LlmResult result = engine.complete("system", List.of(), "вопрос", LlmOptions.DEFAULT);
    long elapsedMs = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();

    assertThat(result.content()).isEqualTo("ok");
    assertThat(elapsedMs).isGreaterThanOrEqualTo(1800L);
    server.verify();
  }

  @Test
  void completeRejectsEmptyBody() {
    server.expect(requestTo(CHAT_URL)).andRespond(withStatus(HttpStatus.OK));

    assertThatThrownBy(() -> engine.complete("system", List.of(), "вопрос", LlmOptions.DEFAULT))
        .isInstanceOf(LlmException.class)
        .hasMessageContaining("Empty response");
    server.verify();
  }

  @Test
  void embedBatchRejectsIncompleteResponse() {
    server
        .expect(requestTo(EMBED_URL))
        .andRespond(
            withSuccess(
                "{\"data\":[{\"embedding\":[0.1,0.2]}],\"usage\":{\"total_tokens\":7}}",
                MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> engine.embedBatch(List.of("первый", "второй")))
        .isInstanceOf(LlmException.class)
        .hasMessageContaining("Incomplete embedding");
    server.verify();
  }

  @Test
  void embedBatchReturnsEmptyForNoInput() {
    EmbeddingResult result = engine.embedBatch(List.of());

    assertThat(result.embeddings()).isEmpty();
    assertThat(result.totalTokens()).isZero();
    server.verify();
  }

  @Test
  void completeOmitsToolFieldsWhenNoToolsAreConfigured() {
    server
        .expect(requestTo(CHAT_URL))
        .andExpect(MockRestRequestMatchers.jsonPath("$.tools").doesNotExist())
        .andExpect(MockRestRequestMatchers.jsonPath("$.tool_choice").doesNotExist())
        .andRespond(
            withSuccess(
                "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"ok\"}}],"
                    + "\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":1,\"total_tokens\":2}}",
                MediaType.APPLICATION_JSON));

    engine.complete("system", List.of(), "вопрос", LlmOptions.DEFAULT);

    server.verify();
  }

  @Test
  void completeSerializesToolsInTheOpenAiFunctionShape() {
    server
        .expect(requestTo(CHAT_URL))
        .andExpect(MockRestRequestMatchers.jsonPath("$.tools[0].type").value("function"))
        .andExpect(
            MockRestRequestMatchers.jsonPath("$.tools[0].function.name").value("create_client"))
        .andExpect(
            MockRestRequestMatchers.jsonPath("$.tools[0].function.parameters.type").value("object"))
        .andExpect(MockRestRequestMatchers.jsonPath("$.tool_choice").value("auto"))
        .andRespond(
            withSuccess(
                "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"ok\"}}],"
                    + "\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":1,\"total_tokens\":2}}",
                MediaType.APPLICATION_JSON));

    engine.complete("system", List.of(), "создай клиента", createClientTools());

    server.verify();
  }

  @Test
  void completeReturnsToolCallsAndFinishReason() {
    server
        .expect(requestTo(CHAT_URL))
        .andRespond(
            withSuccess(
                "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":null,"
                    + "\"tool_calls\":[{\"id\":\"call_1\",\"type\":\"function\",\"function\":"
                    + "{\"name\":\"create_client\",\"arguments\":\"{\\\"fullName\\\":\\\"Пётр\\\"}\"}}]},"
                    + "\"finish_reason\":\"tool_calls\"}],"
                    + "\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":1,\"total_tokens\":2}}",
                MediaType.APPLICATION_JSON));

    LlmResult result = engine.complete("system", List.of(), "создай клиента", createClientTools());

    assertThat(result.finishReason()).isEqualTo("tool_calls");
    assertThat(result.toolCalls())
        .containsExactly(new LlmToolCall("call_1", "create_client", "{\"fullName\":\"Пётр\"}"));
    assertThat(result.content()).isEmpty();
    server.verify();
  }

  @Test
  void completeSendsToolResultMessagesWithTheirToolCallId() {
    server
        .expect(requestTo(CHAT_URL))
        .andExpect(MockRestRequestMatchers.jsonPath("$.messages[1].role").value("assistant"))
        .andExpect(
            MockRestRequestMatchers.jsonPath("$.messages[1].tool_calls[0].function.name")
                .value("create_client"))
        .andExpect(MockRestRequestMatchers.jsonPath("$.messages[2].role").value("tool"))
        .andExpect(MockRestRequestMatchers.jsonPath("$.messages[2].tool_call_id").value("call_1"))
        .andExpect(MockRestRequestMatchers.jsonPath("$.messages[2]").exists())
        .andRespond(
            withSuccess(
                "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"Готово\"}}],"
                    + "\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":1,\"total_tokens\":2}}",
                MediaType.APPLICATION_JSON));

    List<LlmMessage> history =
        List.of(
            LlmMessage.assistant(
                null,
                List.of(new LlmToolCall("call_1", "create_client", "{\"fullName\":\"Пётр\"}"))),
            LlmMessage.tool("call_1", "{\"clientId\":\"7\"}"));

    LlmResult result = engine.complete("system", history, null, LlmOptions.DEFAULT);

    assertThat(result.content()).isEqualTo("Готово");
    server.verify();
  }

  @Test
  void streamAccumulatesToolCallDeltasAndReportsFinishReason() {
    server
        .expect(requestTo(CHAT_URL))
        .andExpect(MockRestRequestMatchers.jsonPath("$.stream").value(true))
        .andExpect(
            MockRestRequestMatchers.jsonPath("$.tools[0].function.name").value("create_client"))
        .andRespond(
            withSuccess(
                String.join(
                    "\n",
                    "data: {\"choices\":[{\"delta\":{\"content\":\"Секунду\"}}]}",
                    "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"call_1\","
                        + "\"type\":\"function\",\"function\":{\"name\":\"create_client\","
                        + "\"arguments\":\"\"}}]}}]}",
                    "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,"
                        + "\"function\":{\"arguments\":\"{\\\"fullName\\\":\\\"\"}}]}}]}",
                    "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,"
                        + "\"function\":{\"arguments\":\"Пётр\\\"}\"}}]}}]}",
                    "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"tool_calls\"}]}",
                    "data: {\"choices\":[],\"usage\":{\"prompt_tokens\":4,"
                        + "\"completion_tokens\":6,\"total_tokens\":10}}",
                    "data: [DONE]",
                    ""),
                MediaType.TEXT_EVENT_STREAM));

    List<String> tokens = new ArrayList<>();
    LlmStreamResult result =
        engine.streamComplete(
            "system", List.of(), "создай клиента", createClientTools(), tokens::add);

    assertThat(tokens).containsExactly("Секунду");
    assertThat(result.finishReason()).isEqualTo("tool_calls");
    assertThat(result.usage().totalTokens()).isEqualTo(10);
    assertThat(result.toolCalls())
        .containsExactly(new LlmToolCall("call_1", "create_client", "{\"fullName\":\"Пётр\"}"));
    server.verify();
  }

  @Test
  void streamWithoutToolCallsStillReturnsTokensAndUsage() {
    server
        .expect(requestTo(CHAT_URL))
        .andRespond(
            withSuccess(
                String.join(
                    "\n",
                    "data: {\"choices\":[{\"delta\":{\"content\":\"При\"}}]}",
                    "data: {\"choices\":[{\"delta\":{\"content\":\"вет\"}},"
                        + "{\"delta\":{\"content\":\"игнор\"}}]}",
                    "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}",
                    "data: {\"choices\":[],\"usage\":{\"prompt_tokens\":1,"
                        + "\"completion_tokens\":2,\"total_tokens\":3}}",
                    "data: [DONE]",
                    ""),
                MediaType.TEXT_EVENT_STREAM));

    List<String> tokens = new ArrayList<>();
    LlmStreamResult result =
        engine.streamComplete("system", List.of(), "привет", LlmOptions.DEFAULT, tokens::add);

    assertThat(tokens).containsExactly("При", "вет");
    assertThat(result.hasToolCalls()).isFalse();
    assertThat(result.finishReason()).isEqualTo("stop");
    assertThat(result.usage().totalTokens()).isEqualTo(3);
    server.verify();
  }

  @Test
  void badRequestErrorNeverCarriesThePromptEchoedByOpenAi() {
    String systemPrompt = "Ты — юридический ассистент PravoOS. СЕКРЕТНАЯ_ИНСТРУКЦИЯ_ПРОМПТА";
    server
        .expect(ExpectedCount.once(), requestTo(CHAT_URL))
        .andRespond(
            withStatus(HttpStatus.BAD_REQUEST)
                .body(
                    "{\"error\":{\"message\":\"Invalid prompt: "
                        + systemPrompt
                        + "\",\"type\":\"invalid_request_error\",\"code\":\"invalid_prompt\"}}")
                .contentType(MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> engine.complete(systemPrompt, List.of(), "вопрос", LlmOptions.DEFAULT))
        .isInstanceOf(LlmException.class)
        .hasMessage("OpenAI chat completion failed with status 400");
    server.verify();
  }

  @Test
  void streamErrorNeverCarriesTheUpstreamResponseBody() {
    String systemPrompt = "Системный промпт с внутренними правилами";
    server
        .expect(ExpectedCount.once(), requestTo(CHAT_URL))
        .andRespond(
            withStatus(HttpStatus.BAD_REQUEST)
                .body("{\"error\":{\"message\":\"" + systemPrompt + "\"}}")
                .contentType(MediaType.APPLICATION_JSON));

    assertThatThrownBy(
            () ->
                engine.streamComplete(
                    systemPrompt, List.of(), "вопрос", LlmOptions.DEFAULT, token -> {}))
        .isInstanceOf(LlmException.class)
        .hasMessage("OpenAI chat stream failed with status 400");
    server.verify();
  }

  @Test
  void transportFailureAfterAllAttemptsReportsOnlyTheOperation() {
    server
        .expect(ExpectedCount.manyTimes(), requestTo(EMBED_URL))
        .andRespond(
            request -> {
              throw new java.io.IOException("connect to openai.test with body Системный промпт");
            });

    assertThatThrownBy(() -> engine.embedBatch(List.of("текст")))
        .isInstanceOf(LlmException.class)
        .hasMessage("OpenAI embedding failed");
  }

  private static LlmOptions createClientTools() {
    ObjectNode parameters = JsonNodeFactory.instance.objectNode();
    parameters.put("type", "object");
    parameters.putObject("properties").putObject("fullName").put("type", "string");
    parameters.putArray("required").add("fullName");
    return LlmOptions.DEFAULT.withTools(
        List.of(new ToolSpec("create_client", "Создать клиента", parameters)),
        LlmOptions.TOOL_CHOICE_AUTO);
  }
}
