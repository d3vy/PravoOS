package com.pravoos.llm.web;

import com.pravoos.llm.domain.EmbeddingResult;
import com.pravoos.llm.domain.LlmResult;
import com.pravoos.llm.domain.LlmUsage;
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
import jakarta.validation.Valid;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

  private final OpenAiEngine engine;
  private final AsyncTaskExecutor taskExecutor;
  private final PromptPiiRedactor piiRedactor;

  public LlmController(
      OpenAiEngine engine, AsyncTaskExecutor taskExecutor, PromptPiiRedactor piiRedactor) {
    this.engine = engine;
    this.taskExecutor = taskExecutor;
    this.piiRedactor = piiRedactor;
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
    return new LlmResult(session.restore(result.content()), result.usage());
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
    taskExecutor.execute(
        () -> {
          try {
            StreamRestorer restorer =
                new StreamRestorer(session, token -> sendToken(emitter, token));
            LlmUsage usage =
                engine.streamComplete(
                    piiRedactor.redact(request.systemPrompt(), session),
                    piiRedactor.redact(request.history(), session),
                    piiRedactor.redact(request.userMessage(), session),
                    request.options(),
                    restorer);
            restorer.flush();
            piiRedactor.recordSession(session);
            emitter.send(SseEmitter.event().name("usage").data(usage, MediaType.APPLICATION_JSON));
            emitter.complete();
          } catch (StreamClosedException e) {
            log.info("Streaming completion aborted: client closed the connection");
            emitter.complete();
          } catch (Exception e) {
            log.error("Streaming completion failed", e);
            signalStreamFailure(emitter, e);
          }
        });
    return emitter;
  }

  private void signalStreamFailure(SseEmitter emitter, Exception failure) {
    try {
      emitter.send(
          SseEmitter.event()
              .name("error")
              .data(new StreamError(failure.getMessage()), MediaType.APPLICATION_JSON));
      emitter.complete();
    } catch (IOException | RuntimeException sendFailure) {
      emitter.completeWithError(failure);
    }
  }

  private void sendToken(SseEmitter emitter, String token) {
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
