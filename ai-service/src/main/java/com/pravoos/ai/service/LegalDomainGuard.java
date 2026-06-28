package com.pravoos.ai.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.pravoos.ai.config.OpenAiProperties;
import com.pravoos.ai.exception.NonLegalQueryException;
import com.pravoos.ai.llm.dto.LlmMessage;
import com.pravoos.ai.llm.dto.OpenAiChatRequest;
import com.pravoos.ai.llm.dto.OpenAiChatResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class LegalDomainGuard {

    private static final Logger log = LoggerFactory.getLogger(LegalDomainGuard.class);

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Duration CACHE_TTL = Duration.ofHours(1);
    private static final long CACHE_MAX_SIZE = 10_000L;

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
    private final Cache<String, Boolean> verdictCache;
    private final Counter passCounter;
    private final Counter blockCounter;
    private final Counter failOpenCounter;
    private final Counter cacheHitCounter;

    public LegalDomainGuard(@Qualifier("openAiGuardRestClient") RestClient openAiGuardRestClient,
                            OpenAiProperties properties,
                            MeterRegistry registry) {
        this.restClient = openAiGuardRestClient;
        this.properties = properties;
        this.verdictCache = Caffeine.newBuilder()
                .maximumSize(CACHE_MAX_SIZE)
                .expireAfterWrite(CACHE_TTL)
                .build();
        this.passCounter = guardCounter(registry, "pass");
        this.blockCounter = guardCounter(registry, "block");
        this.failOpenCounter = guardCounter(registry, "allow_failopen");
        this.cacheHitCounter = guardCounter(registry, "cache_hit");
    }

    public void assertLegalQuery(String userMessage) {
        Boolean cached = verdictCache.getIfPresent(cacheKey(userMessage));
        if (cached != null) {
            cacheHitCounter.increment();
            enforce(cached);
            return;
        }
        enforce(classify(userMessage));
    }

    private boolean classify(String userMessage) {
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
                failOpenCounter.increment();
                return true;
            }
            verdict = response.firstContent().trim().toUpperCase();
        } catch (RestClientException e) {
            log.warn("Guard classifier unavailable, allowing query (fail-open): {}", e.getMessage());
            failOpenCounter.increment();
            return true;
        }

        boolean legal = "YES".equals(verdict);
        verdictCache.put(cacheKey(userMessage), legal);
        log.debug("Guard verdict '{}' for message: {}", verdict,
                userMessage.substring(0, Math.min(50, userMessage.length())));
        return legal;
    }

    private void enforce(boolean legal) {
        if (legal) {
            passCounter.increment();
            return;
        }
        blockCounter.increment();
        log.info("Non-legal query blocked by guard classifier");
        throw new NonLegalQueryException();
    }

    private String cacheKey(String userMessage) {
        return WHITESPACE.matcher(userMessage.trim().toLowerCase()).replaceAll(" ");
    }

    private Counter guardCounter(MeterRegistry registry, String result) {
        return Counter.builder("pravoos.guard")
                .description("Legal-domain guard classifier outcomes")
                .tag("result", result)
                .register(registry);
    }
}
