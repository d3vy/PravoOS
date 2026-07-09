package com.pravoos.llm.web;

import com.pravoos.llm.domain.EmbeddingResult;
import com.pravoos.llm.domain.LlmResult;
import com.pravoos.llm.domain.LlmUsage;
import com.pravoos.llm.openai.OpenAiEngine;
import com.pravoos.llm.web.dto.CompleteRequest;
import com.pravoos.llm.web.dto.EmbedBatchRequest;
import com.pravoos.llm.web.dto.EmbedRequest;
import com.pravoos.llm.web.dto.EmbedResponse;
import com.pravoos.llm.web.dto.StreamToken;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

@RestController
@RequestMapping("/internal/llm")
public class LlmController {

    private static final Logger log = LoggerFactory.getLogger(LlmController.class);
    private static final long STREAM_TIMEOUT_MS = 180_000L;

    private final OpenAiEngine engine;
    private final AsyncTaskExecutor taskExecutor;

    public LlmController(OpenAiEngine engine, AsyncTaskExecutor taskExecutor) {
        this.engine = engine;
        this.taskExecutor = taskExecutor;
    }

    @PostMapping("/complete")
    public LlmResult complete(@Valid @RequestBody CompleteRequest request) {
        return engine.complete(request.systemPrompt(), request.history(), request.userMessage(), request.options());
    }

    @PostMapping("/embed")
    public EmbedResponse embed(@Valid @RequestBody EmbedRequest request) {
        return new EmbedResponse(engine.embed(request.text()));
    }

    @PostMapping("/embed-batch")
    public EmbeddingResult embedBatch(@Valid @RequestBody EmbedBatchRequest request) {
        return engine.embedBatch(request.texts());
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@Valid @RequestBody CompleteRequest request) {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        taskExecutor.execute(() -> {
            try {
                LlmUsage usage = engine.streamComplete(request.systemPrompt(), request.history(),
                        request.userMessage(), request.options(),
                        token -> sendToken(emitter, token));
                emitter.send(SseEmitter.event().name("usage").data(usage, MediaType.APPLICATION_JSON));
                emitter.complete();
            } catch (Exception e) {
                log.error("Streaming completion failed", e);
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

    private void sendToken(SseEmitter emitter, String token) {
        try {
            emitter.send(SseEmitter.event().name("token").data(new StreamToken(token), MediaType.APPLICATION_JSON));
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
