package com.pravoos.ai.llm.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pravoos.ai.llm.api.*;
import com.pravoos.ai.llm.internal.dto.OpenAiChatRequest;
import com.pravoos.ai.llm.internal.dto.OpenAiChatResponse;
import com.pravoos.ai.llm.internal.dto.OpenAiChatStreamRequest;
import com.pravoos.ai.llm.internal.dto.OpenAiEmbeddingRequest;
import com.pravoos.ai.llm.internal.dto.OpenAiEmbeddingResponse;
import com.pravoos.ai.llm.internal.dto.OpenAiStreamChunk;
import com.pravoos.ai.shared.config.OpenAiProperties;
import com.pravoos.ai.shared.exception.LlmException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

@Component
public class OpenAiLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiLlmClient.class);
    private static final int MAX_ATTEMPTS = 3;
    private static final long BASE_BACKOFF_MS = 500L;
    private static final long MAX_BACKOFF_MS = 8000L;
    private static final Set<Integer> RETRYABLE_STATUSES = Set.of(429, 500, 502, 503, 504);
    private static final String STREAM_DONE_MARKER = "[DONE]";

    private final RestClient restClient;
    private final OpenAiProperties properties;
    private final LlmMetrics llmMetrics;
    private final ObjectMapper objectMapper;
    private final Semaphore inFlightLimit;
    private final long acquireTimeoutMs;

    public OpenAiLlmClient(RestClient openAiRestClient,
                           OpenAiProperties properties,
                           LlmMetrics llmMetrics,
                           ObjectMapper objectMapper,
                           @Value("${llm.openai.max-concurrent-requests:20}") int maxConcurrentRequests,
                           @Value("${llm.openai.acquire-timeout-ms:2000}") long acquireTimeoutMs) {
        this.restClient = openAiRestClient;
        this.properties = properties;
        this.llmMetrics = llmMetrics;
        this.objectMapper = objectMapper;
        this.inFlightLimit = new Semaphore(maxConcurrentRequests);
        this.acquireTimeoutMs = acquireTimeoutMs;
    }

    @Override
    public LlmResult complete(String systemPrompt, List<LlmMessage> history, String userMessage) {
        List<LlmMessage> messages = buildMessages(systemPrompt, history, userMessage);
        OpenAiChatRequest request = new OpenAiChatRequest(
                properties.model(),
                messages,
                properties.maxTokens(),
                0.1
        );

        OpenAiChatResponse response = executeWithRetry("chat completion", () -> restClient.post()
                .uri("/chat/completions")
                .body(request)
                .retrieve()
                .body(OpenAiChatResponse.class));

        if (response == null) {
            throw new LlmException("Empty response from LLM API");
        }
        LlmUsage usage = response.toLlmUsage();
        llmMetrics.recordCompletion(usage);
        log.debug("LLM completion successful, model: {}, tokens: {}", properties.model(), usage.totalTokens());
        return new LlmResult(response.firstContent(), usage);
    }

    @Override
    public LlmUsage streamComplete(String systemPrompt, List<LlmMessage> history, String userMessage,
                                   Consumer<String> tokenConsumer) {
        List<LlmMessage> messages = buildMessages(systemPrompt, history, userMessage);
        OpenAiChatStreamRequest request = OpenAiChatStreamRequest.withUsage(
                properties.model(), messages, properties.maxTokens(), 0.1);

        acquireSlot("chat stream");
        try {
            LlmUsage usage = restClient.post()
                    .uri("/chat/completions")
                    .body(request)
                    .exchange((clientRequest, clientResponse) -> {
                        if (clientResponse.getStatusCode().isError()) {
                            String body = readErrorBody(clientResponse.getBody());
                            log.error("OpenAI chat stream returned {}: {}",
                                    clientResponse.getStatusCode().value(), body);
                            throw new LlmException("OpenAI chat stream failed with status "
                                    + clientResponse.getStatusCode().value());
                        }
                        return consumeStream(clientResponse.getBody(), tokenConsumer);
                    });

            LlmUsage resolved = usage == null ? LlmUsage.EMPTY : usage;
            llmMetrics.recordCompletion(resolved);
            log.debug("LLM stream completed, model: {}, tokens: {}", properties.model(), resolved.totalTokens());
            return resolved;
        } finally {
            inFlightLimit.release();
        }
    }

    private LlmUsage consumeStream(java.io.InputStream body, Consumer<String> tokenConsumer) throws IOException {
        LlmUsage usage = LlmUsage.EMPTY;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty() || !line.startsWith("data:")) {
                    continue;
                }
                String payload = line.substring("data:".length()).trim();
                if (payload.isEmpty()) {
                    continue;
                }
                if (STREAM_DONE_MARKER.equals(payload)) {
                    break;
                }
                OpenAiStreamChunk chunk = objectMapper.readValue(payload, OpenAiStreamChunk.class);
                if (chunk.usage() != null) {
                    usage = chunk.toLlmUsage();
                }
                String delta = chunk.firstDelta();
                if (delta != null && !delta.isEmpty()) {
                    tokenConsumer.accept(delta);
                }
            }
        }
        return usage;
    }

    private String readErrorBody(java.io.InputStream body) {
        try {
            return StreamUtils.copyToString(body, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "<unreadable body: " + e.getMessage() + ">";
        }
    }

    @Override
    public float[] embed(String text) {
        return embedBatch(List.of(text)).embeddings().get(0);
    }

    @Override
    public EmbeddingResult embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return new EmbeddingResult(List.of(), 0L);
        }
        OpenAiEmbeddingRequest request = new OpenAiEmbeddingRequest(
                properties.embeddingModel(),
                texts
        );

        OpenAiEmbeddingResponse response = executeWithRetry("embedding", () -> restClient.post()
                .uri("/embeddings")
                .body(request)
                .retrieve()
                .body(OpenAiEmbeddingResponse.class));

        if (response == null || response.allEmbeddings().size() != texts.size()) {
            throw new LlmException("Incomplete embedding response from API");
        }
        return new EmbeddingResult(response.allEmbeddings(), response.totalTokens());
    }

    private <T> T executeWithRetry(String operation, Supplier<T> call) {
        acquireSlot(operation);
        try {
            return executeWithRetryInternal(operation, call);
        } finally {
            inFlightLimit.release();
        }
    }

    private void acquireSlot(String operation) {
        boolean acquired;
        try {
            acquired = inFlightLimit.tryAcquire(acquireTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new LlmException("OpenAI " + operation + " interrupted while waiting for a slot");
        }
        if (!acquired) {
            log.warn("OpenAI {} rejected: concurrency limit reached ({} in flight)",
                    operation, inFlightLimit.availablePermits());
            throw new LlmException("OpenAI " + operation + " overloaded, please retry shortly");
        }
    }

    private <T> T executeWithRetryInternal(String operation, Supplier<T> call) {
        RestClientException lastException = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return call.get();
            } catch (RestClientResponseException e) {
                lastException = e;
                int status = e.getStatusCode().value();
                if (!RETRYABLE_STATUSES.contains(status) || attempt == MAX_ATTEMPTS) {
                    log.error("OpenAI {} returned {}: {}", operation, status, e.getResponseBodyAsString());
                    throw new LlmException("OpenAI " + operation + " failed with status " + status);
                }
                log.warn("OpenAI {} returned {} (attempt {}/{}), retrying", operation, status, attempt, MAX_ATTEMPTS);
            } catch (RestClientException e) {
                lastException = e;
                if (attempt == MAX_ATTEMPTS) {
                    log.error("OpenAI {} failed: {}", operation, e.getMessage());
                    throw new LlmException("OpenAI " + operation + " failed: " + e.getMessage());
                }
                log.warn("OpenAI {} failed (attempt {}/{}): {}, retrying", operation, attempt, MAX_ATTEMPTS, e.getMessage());
            }
            backoff(attempt);
        }
        throw new LlmException("OpenAI " + operation + " failed after " + MAX_ATTEMPTS + " attempts: "
                + (lastException == null ? "unknown" : lastException.getMessage()));
    }

    private void backoff(int attempt) {
        long delay = Math.min(BASE_BACKOFF_MS * (1L << (attempt - 1)), MAX_BACKOFF_MS);
        long jitter = ThreadLocalRandom.current().nextLong(delay / 2 + 1);
        try {
            Thread.sleep(delay + jitter);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new LlmException("OpenAI call interrupted during backoff");
        }
    }

    private List<LlmMessage> buildMessages(String systemPrompt, List<LlmMessage> history, String userMessage) {
        List<LlmMessage> messages = new ArrayList<>();
        messages.add(new LlmMessage("system", systemPrompt));
        messages.addAll(history);
        messages.add(new LlmMessage("user", userMessage));
        return messages;
    }
}
