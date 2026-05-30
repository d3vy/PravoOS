package com.pravoos.ai.llm;

import com.pravoos.ai.config.DeepSeekProperties;
import com.pravoos.ai.exception.LlmException;
import com.pravoos.ai.llm.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.List;

@Component
public class DeepSeekLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekLlmClient.class);

    private final RestClient restClient;
    private final DeepSeekProperties properties;

    public DeepSeekLlmClient(RestClient deepSeekRestClient, DeepSeekProperties properties) {
        this.restClient = deepSeekRestClient;
        this.properties = properties;
    }

    @Override
    public String complete(String systemPrompt, List<LlmMessage> history, String userMessage) {
        List<LlmMessage> messages = buildMessages(systemPrompt, history, userMessage);
        DeepSeekChatRequest request = new DeepSeekChatRequest(
                properties.model(),
                messages,
                properties.maxTokens(),
                0.1
        );

        try {
            DeepSeekChatResponse response = restClient.post()
                    .uri("/chat/completions")
                    .body(request)
                    .retrieve()
                    .body(DeepSeekChatResponse.class);

            if (response == null) {
                throw new LlmException("Empty response from LLM API");
            }
            log.debug("LLM completion successful, model: {}", properties.model());
            return response.firstContent();
        } catch (RestClientException e) {
            log.error("LLM API call failed: {}", e.getMessage());
            throw new LlmException("LLM API call failed: " + e.getMessage());
        }
    }

    @Override
    public float[] embed(String text) {
        DeepSeekEmbeddingRequest request = new DeepSeekEmbeddingRequest(
                properties.embeddingModel(),
                text
        );

        try {
            DeepSeekEmbeddingResponse response = restClient.post()
                    .uri("/embeddings")
                    .body(request)
                    .retrieve()
                    .body(DeepSeekEmbeddingResponse.class);

            if (response == null || response.firstEmbedding().length == 0) {
                throw new LlmException("Empty embedding response from API");
            }
            return response.firstEmbedding();
        } catch (RestClientException e) {
            log.error("Embedding API call failed: {}", e.getMessage());
            throw new LlmException("Embedding API call failed: " + e.getMessage());
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
