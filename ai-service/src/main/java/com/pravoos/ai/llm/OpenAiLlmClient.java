package com.pravoos.ai.llm;

import com.pravoos.ai.config.OpenAiProperties;
import com.pravoos.ai.exception.LlmException;
import com.pravoos.ai.llm.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.List;

@Component
public class OpenAiLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiLlmClient.class);

    private final RestClient restClient;
    private final OpenAiProperties properties;

    public OpenAiLlmClient(RestClient openAiRestClient, OpenAiProperties properties) {
        this.restClient = openAiRestClient;
        this.properties = properties;
    }

    @Override
    public String complete(String systemPrompt, List<LlmMessage> history, String userMessage) {
        List<LlmMessage> messages = buildMessages(systemPrompt, history, userMessage);
        OpenAiChatRequest request = new OpenAiChatRequest(
                properties.model(),
                messages,
                properties.maxTokens(),
                0.1
        );

        try {
            OpenAiChatResponse response = restClient.post()
                    .uri("/chat/completions")
                    .body(request)
                    .retrieve()
                    .body(OpenAiChatResponse.class);

            if (response == null) {
                throw new LlmException("Empty response from LLM API");
            }
            log.debug("LLM completion successful, model: {}", properties.model());
            return response.firstContent();
        } catch (RestClientResponseException e) {
            log.error("LLM API returned {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new LlmException("LLM API call failed with status " + e.getStatusCode().value());
        } catch (RestClientException e) {
            log.error("LLM API call failed: {}", e.getMessage());
            throw new LlmException("LLM API call failed: " + e.getMessage());
        }
    }

    @Override
    public float[] embed(String text) {
        return embedBatch(List.of(text)).get(0);
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        OpenAiEmbeddingRequest request = new OpenAiEmbeddingRequest(
                properties.embeddingModel(),
                texts
        );

        try {
            OpenAiEmbeddingResponse response = restClient.post()
                    .uri("/embeddings")
                    .body(request)
                    .retrieve()
                    .body(OpenAiEmbeddingResponse.class);

            if (response == null || response.allEmbeddings().size() != texts.size()) {
                throw new LlmException("Incomplete embedding response from API");
            }
            return response.allEmbeddings();
        } catch (RestClientResponseException e) {
            log.error("Embedding API returned {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new LlmException("Embedding API call failed with status " + e.getStatusCode().value());
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
