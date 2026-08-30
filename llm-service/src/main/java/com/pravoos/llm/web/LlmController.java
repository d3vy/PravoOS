package com.pravoos.llm.web;

import com.pravoos.llm.domain.EmbeddingResult;
import com.pravoos.llm.domain.LlmResult;
import com.pravoos.llm.domain.LlmStreamResult;
import com.pravoos.llm.domain.LlmToolCall;
import com.pravoos.llm.exception.LlmException;
import com.pravoos.llm.openai.LlmMetrics;
import com.pravoos.llm.openai.OpenAiEngine;
import com.pravoos.llm.pii.PromptPiiRedactor;
import com.pravoos.llm.pii.RedactionSession;
import com.pravoos.llm.pii.StreamRestorer;
import com.pravoos.llm.web.dto.CompleteRequest;
import com.pravoos.llm.web.dto.EmbedBatchRequest;
import com.pravoos.llm.web.dto.EmbedRequest;
import com.pravoos.llm.web.dto.EmbedResponse;
import com.pravoos.llm.web.dto.StreamError;
import com.pravoos.llm.web.dto.StreamToken;
import com.pravoos.llm.web.dto.StreamToolCalls;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/internal/llm")
public class LlmController {

  private static final Logger log = LoggerFactory.getLogger(LlmController.class);
  private static final long STREAM_TIMEOUT_MS = 180_000L;
  private static final String GENERIC_STREAM_FAILURE = "Streaming completion failed";

  private final OpenAiEngine engine;
  private final AsyncTaskExecutor taskExecutor;
  private final PromptPiiRedactor piiRedactor;
  private final LlmMetrics metrics;

  public LlmController(
      OpenAiEngine engine,
      @Qualifier("llmStreamExecutor") AsyncTaskExecutor taskExecutor,
      PromptPiiRedactor piiRedactor,
      LlmMetrics metrics) {
    this.engine = engine;
    this.taskExecutor = taskExecutor;
    this.piiRedactor = piiRedactor;
    this.metrics = metrics;
  }

  @PostMapping("/complete")
  public LlmResult complete(@Valid @RequestBody CompleteRequest request) {
    RedactionSession session = piiRedactor.newSession();
    LlmResult result =
        engine.complete(
            piiRedactor.redact(request.systemPrompt(), session),
            piiRedactor.redact(request.history(), session),
            piiRedactor.redact(request.userMessage(), session),
            request.options());
    piiRedactor.recordSession(session);
    return new LlmResult(
        session.restore(result.content()),
        result.usage(),
        piiRedactor.restoreToolCalls(result.toolCalls(), session),
        result.finishReason());
  }

  @PostMapping("/embed")
  public EmbedResponse embed(@Valid @RequestBody EmbedRequest request) {
    RedactionSession session = piiRedactor.newSession();
    EmbedResponse response =
        new EmbedResponse(engine.embed(piiRedactor.redactForEmbedding(request.text(), session)));
    piiRedactor.recordSession(session);
    return response;
  }

  @PostMapping("/embed-batch")
  public EmbeddingResult embedBatch(@Valid @RequestBody EmbedBatchRequest request) {
    RedactionSession session = piiRedactor.newSession();
    EmbeddingResult result =
        engine.embedBatch(
            request.texts().stream()
                .map(text -> piiRedactor.redactForEmbedding(text, session))
                .toList());
    piiRedactor.recordSession(session);
    return result;
  }

  @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter stream(@Valid @RequestBody CompleteRequest request) {
    SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
    RedactionSession session = piiRedactor.newSession();
    AtomicBoolean cancelled = new AtomicBoolean(false);
    AtomicReference<Thread> workerThread = new AtomicReference<>();

    metrics.streamStarted();
    emitter.onCompletion(metrics::streamEnded);
    emitter.onTimeout(
        () -> {
          log.warn("Streaming completion timed out after {} ms", STREAM_TIMEOUT_MS);
          cancel(cancelled, workerThread);
        });
    emitter.onError(
        ex -> {
          log.info("Streaming completion aborted: {}", ex.toString());
          cancel(cancelled, workerThread);
        });

    Runnable streamTask =
        () -> {
          workerThread.set(Thread.currentThread());
          try {
            StreamRestorer restorer =
                new StreamRestorer(session, token -> sendToken(emitter, cancelled, token));
            LlmStreamResult result =
                engine.streamComplete(
                    piiRedactor.redact(request.systemPrompt(), session),
                    piiRedactor.redact(request.history(), session),
                    piiRedactor.redact(request.userMessage(), session),
                    request.options(),
                    restorer);
            restorer.flush();
            piiRedactor.recordSession(session);
            if (result.hasToolCalls()) {
              sendToolCalls(
                  emitter, cancelled, piiRedactor.restoreToolCalls(result.toolCalls(), session));
            }
            if (cancelled.get()) {
              return;
            }
            emitter.send(
                SseEmitter.event().name("usage").data(result.usage(), MediaType.APPLICATION_JSON));
            emitter.complete();
          } catch (StreamClosedException e) {
            log.info("Streaming completion aborted: client closed the connection");
            if (!cancelled.get()) {
              emitter.complete();
            }
          } catch (Exception e) {
            log.error("Streaming completion failed", e);
            if (!cancelled.get()) {
              signalStreamFailure(emitter, e);
            }
          } finally {
            workerThread.set(null);
          }
        };

    try {
      taskExecutor.execute(streamTask);
    } catch (RejectedExecutionException e) {
      metrics.streamEnded();
      log.warn("Streaming completion rejected: stream pool is saturated");
      throw new LlmException("Сервис перегружен, попробуйте повторить запрос позже");
    }
    return emitter;
  }

  private static void cancel(AtomicBoolean cancelled, AtomicReference<Thread> workerThread) {
    if (cancelled.compareAndSet(false, true)) {
      Thread thread = workerThread.get();
      if (thread != null) {
        thread.interrupt();
      }
    }
  }

  private void signalStreamFailure(SseEmitter emitter, Exception failure) {
    try {
      emitter.send(
          SseEmitter.event()
              .name("error")
              .data(new StreamError(safeFailureMessage(failure)), MediaType.APPLICATION_JSON));
      emitter.complete();
    } catch (IOException | RuntimeException sendFailure) {
      emitter.completeWithError(failure);
    }
  }

  static String safeFailureMessage(Exception failure) {
    return failure instanceof LlmException ? failure.getMessage() : GENERIC_STREAM_FAILURE;
  }

  private void sendToolCalls(
      SseEmitter emitter, AtomicBoolean cancelled, List<LlmToolCall> toolCalls) {
    if (cancelled.get()) {
      throw new StreamClosedException(null);
    }
    try {
      emitter.send(
          SseEmitter.event()
              .name("tool_calls")
              .data(new StreamToolCalls(toolCalls), MediaType.APPLICATION_JSON));
    } catch (IOException e) {
      throw new StreamClosedException(e);
    }
  }

  private void sendToken(SseEmitter emitter, AtomicBoolean cancelled, String token) {
    if (cancelled.get()) {
      throw new StreamClosedException(null);
    }
    try {
      emitter.send(
          SseEmitter.event()
              .name("token")
              .data(new StreamToken(token), MediaType.APPLICATION_JSON));
    } catch (IOException e) {
      throw new StreamClosedException(e);
    }
  }

  private static final class StreamClosedException extends RuntimeException {
    StreamClosedException(Throwable cause) {
      super(cause);
    }
  }
}
