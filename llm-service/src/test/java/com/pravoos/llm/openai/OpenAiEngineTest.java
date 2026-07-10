package com.pravoos.llm.openai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pravoos.llm.config.OpenAiProperties;
import com.pravoos.llm.domain.EmbeddingResult;
import com.pravoos.llm.domain.LlmOptions;
import com.pravoos.llm.domain.LlmResult;
import com.pravoos.llm.exception.LlmException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

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
        OpenAiProperties properties = new OpenAiProperties(
                "test-key", BASE_URL, "gpt-model", "gpt-guard", "embed-model", 1536, 2048);
        LlmMetrics metrics = new LlmMetrics(new SimpleMeterRegistry());
        engine = new OpenAiEngine(restClient, properties, metrics, new ObjectMapper(), 5, 2000L);
    }

    @Test
    void completeReturnsContentAndUsage() {
        server.expect(requestTo(CHAT_URL))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andRespond(withSuccess(
                        "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"Ответ\"}}],"
                                + "\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":5,\"total_tokens\":15}}",
                        MediaType.APPLICATION_JSON));

        LlmResult result = engine.complete("system", List.of(), "вопрос", LlmOptions.DEFAULT);

        assertThat(result.content()).isEqualTo("Ответ");
        assertThat(result.usage().totalTokens()).isEqualTo(15);
        server.verify();
    }

    @Test
    void completeDoesNotRetryNonRetryableStatus() {
        server.expect(ExpectedCount.once(), requestTo(CHAT_URL))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .body("{\"error\":\"bad request\"}").contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> engine.complete("system", List.of(), "вопрос", LlmOptions.DEFAULT))
                .isInstanceOf(LlmException.class)
                .hasMessageContaining("400");
        server.verify();
    }

    @Test
    void completeRetriesRetryableStatusThenSucceeds() {
        server.expect(ExpectedCount.once(), requestTo(CHAT_URL))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(ExpectedCount.once(), requestTo(CHAT_URL))
                .andRespond(withSuccess(
                        "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"ok\"}}],"
                                + "\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":1,\"total_tokens\":2}}",
                        MediaType.APPLICATION_JSON));

        LlmResult result = engine.complete("system", List.of(), "вопрос", LlmOptions.DEFAULT);

        assertThat(result.content()).isEqualTo("ok");
        server.verify();
    }

    @Test
    void completeRejectsEmptyBody() {
        server.expect(requestTo(CHAT_URL))
                .andRespond(withStatus(HttpStatus.OK));

        assertThatThrownBy(() -> engine.complete("system", List.of(), "вопрос", LlmOptions.DEFAULT))
                .isInstanceOf(LlmException.class)
                .hasMessageContaining("Empty response");
        server.verify();
    }

    @Test
    void embedBatchRejectsIncompleteResponse() {
        server.expect(requestTo(EMBED_URL))
                .andRespond(withSuccess(
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
}
