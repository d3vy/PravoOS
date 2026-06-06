package com.pravoos.ai.service;

import com.pravoos.ai.config.OpenAiProperties;
import com.pravoos.ai.exception.NonLegalQueryException;
import com.pravoos.ai.llm.dto.LlmMessage;
import com.pravoos.ai.llm.dto.OpenAiChatRequest;
import com.pravoos.ai.llm.dto.OpenAiChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

@Service
public class LegalDomainGuard {

    private static final Logger log = LoggerFactory.getLogger(LegalDomainGuard.class);

    private static final String CLASSIFIER_SYSTEM_PROMPT = """
            Ты — классификатор запросов для юридической платформы.
            Определи, является ли сообщение пользователя юридическим вопросом.
            Юридическим считается любой вопрос, связанный с: правом, законами, нормативными актами,
            судебной практикой, договорами, процессуальными действиями, правовым статусом,
            юридической ответственностью, банкротством, корпоративным правом или смежными темами.
            Отвечай строго одним словом: YES или NO.
            """;

    private final RestClient restClient;
    private final OpenAiProperties properties;

    public LegalDomainGuard(RestClient openAiRestClient, OpenAiProperties properties) {
        this.restClient = openAiRestClient;
        this.properties = properties;
    }

    public void assertLegalQuery(String userMessage) {
        OpenAiChatRequest request = new OpenAiChatRequest(
                properties.guardModel(),
                List.of(
                        new LlmMessage("system", CLASSIFIER_SYSTEM_PROMPT),
                        new LlmMessage("user", userMessage)
                ),
                3,
                0.0
        );

        String verdict;
        try {
            OpenAiChatResponse response = restClient.post()
                    .uri("/chat/completions")
                    .body(request)
                    .retrieve()
                    .body(OpenAiChatResponse.class);

            if (response == null) {
                log.warn("Guard classifier returned empty response, allowing query (fail-open)");
                return;
            }
            verdict = response.firstContent().trim().toUpperCase();
        } catch (RestClientException e) {
            log.warn("Guard classifier unavailable, allowing query (fail-open): {}", e.getMessage());
            return;
        }

        log.debug("Guard verdict '{}' for message: {}", verdict, userMessage.substring(0, Math.min(50, userMessage.length())));

        if (!"YES".equals(verdict)) {
            log.info("Non-legal query blocked by guard classifier");
            throw new NonLegalQueryException();
        }
    }
}
