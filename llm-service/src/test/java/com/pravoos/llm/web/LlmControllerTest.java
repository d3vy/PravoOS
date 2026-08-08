package com.pravoos.llm.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pravoos.llm.domain.EmbeddingResult;
import com.pravoos.llm.domain.LlmResult;
import com.pravoos.llm.domain.LlmUsage;
import com.pravoos.llm.exception.GlobalExceptionHandler;
import com.pravoos.llm.openai.OpenAiEngine;
import com.pravoos.llm.pii.PromptPiiRedactor;
import com.pravoos.llm.pii.RedactionSession;
import com.pravoos.llm.web.dto.CompleteRequest;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class LlmControllerTest {

  private OpenAiEngine engine;
  private PromptPiiRedactor piiRedactor;
  private MockMvc mockMvc;
  private LlmController controller;
  private final ObjectMapper objectMapper = new ObjectMapper();

  private static final class SynchronousTaskExecutor implements AsyncTaskExecutor {
    @Override
    public void execute(Runnable task) {
      task.run();
    }
  }

  @BeforeEach
  void setUp() {
    engine = mock(OpenAiEngine.class);
    piiRedactor = mock(PromptPiiRedactor.class);
    when(piiRedactor.newSession()).thenReturn(new RedactionSession());
    when(piiRedactor.redact(anyString(), any())).thenAnswer(inv -> inv.getArgument(0));
    when(piiRedactor.redact(anyList(), any())).thenAnswer(inv -> inv.getArgument(0));
    when(piiRedactor.redactForEmbedding(anyString(), any())).thenAnswer(inv -> inv.getArgument(0));

    controller = new LlmController(engine, new SynchronousTaskExecutor(), piiRedactor);
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  void completeReturnsRestoredContentAndRecordsSession() throws Exception {
    LlmUsage usage = new LlmUsage(3, 2, 5);
    when(engine.complete(anyString(), anyList(), anyString(), any()))
        .thenReturn(new LlmResult("[NAME_1] здравствуйте", usage));

    mockMvc
        .perform(
            post("/internal/llm/complete")
                .contentType("application/json")
                .content(
                    objectMapper.writeValueAsString(
                        new java.util.LinkedHashMap<>() {
                          {
                            put("systemPrompt", "system");
                            put("history", List.of());
                            put("userMessage", "вопрос");
                            put("options", null);
                          }
                        })))
        .andExpect(status().isOk())
        .andExpect(content().contentType("application/json"));

    verify(piiRedactor).recordSession(any());
  }

  @Test
  void completeReturns400OnMissingUserMessage() throws Exception {
    mockMvc
        .perform(
            post("/internal/llm/complete")
                .contentType("application/json")
                .content("{\"systemPrompt\":\"s\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void embedDelegatesRedactedTextToEngine() throws Exception {
    when(engine.embed("hello")).thenReturn(new float[] {0.1f, 0.2f});

    mockMvc
        .perform(
            post("/internal/llm/embed")
                .contentType("application/json")
                .content("{\"text\":\"hello\"}"))
        .andExpect(status().isOk());

    verify(engine).embed("hello");
    verify(piiRedactor).recordSession(any());
  }

  @Test
  void embedBatchRedactsEachText() throws Exception {
    when(engine.embedBatch(List.of("a", "b")))
        .thenReturn(new EmbeddingResult(List.of(new float[] {1f}, new float[] {2f}), 4));

    mockMvc
        .perform(
            post("/internal/llm/embed-batch")
                .contentType("application/json")
                .content("{\"texts\":[\"a\",\"b\"]}"))
        .andExpect(status().isOk());

    verify(engine).embedBatch(List.of("a", "b"));
  }

  /**
   * Captures the streaming task instead of running it immediately, so the test can drive it on
   * demand — mirroring how the controller hands work off to a real thread pool, without pulling in
   * a real container response (MockMvc's standalone SSE dispatch trips an unrelated framework NPE
   * when writing early-buffered SSE events, so we exercise the controller's wiring directly).
   */
  private static final class CapturingTaskExecutor implements AsyncTaskExecutor {
    private Runnable capturedTask;

    @Override
    public void execute(Runnable task) {
      this.capturedTask = task;
    }
  }

  @Test
  void streamDelegatesToEngineWithRedactedArgsAndRecordsSession() {
    doAnswer(
            invocation -> {
              @SuppressWarnings("unchecked")
              Consumer<String> restorer = invocation.getArgument(4);
              restorer.accept("Привет");
              return new LlmUsage(1, 1, 2);
            })
        .when(engine)
        .streamComplete(anyString(), anyList(), anyString(), any(), any());
    CapturingTaskExecutor executor = new CapturingTaskExecutor();
    LlmController streamingController = new LlmController(engine, executor, piiRedactor);

    SseEmitter emitter =
        streamingController.stream(new CompleteRequest("system", List.of(), "вопрос", null));
    assertThat(emitter).isNotNull();
    executor.capturedTask.run();

    verify(engine).streamComplete(eq("system"), eq(List.of()), eq("вопрос"), any(), any());
    verify(piiRedactor).recordSession(any());
  }

  @Test
  void streamCompletesWithoutPropagatingWhenEngineThrows() {
    when(engine.streamComplete(anyString(), anyList(), anyString(), any(), any()))
        .thenThrow(new RuntimeException("boom"));
    CapturingTaskExecutor executor = new CapturingTaskExecutor();
    LlmController streamingController = new LlmController(engine, executor, piiRedactor);

    streamingController.stream(new CompleteRequest("system", List.of(), "вопрос", null));
    executor.capturedTask.run();

    verify(piiRedactor, org.mockito.Mockito.never()).recordSession(any());
  }
}
