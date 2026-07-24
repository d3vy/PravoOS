package com.pravoos.ai.core.internal.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.pravoos.ai.llm.api.LlmClient;
import com.pravoos.ai.llm.api.LlmOptions;
import com.pravoos.ai.llm.api.LlmResult;
import com.pravoos.ai.shared.exception.LlmException;
import com.pravoos.ai.shared.exception.NonLegalQueryException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class LegalDomainGuard {

  private static final Logger log = LoggerFactory.getLogger(LegalDomainGuard.class);

  private static final Pattern WHITESPACE = Pattern.compile("\\s+");
  private static final Duration CACHE_TTL = Duration.ofHours(1);
  private static final long CACHE_MAX_SIZE = 10_000L;
  private static final int GUARD_MAX_TOKENS = 3;
  private static final double GUARD_TEMPERATURE = 0.0;

  private static final String CLASSIFIER_SYSTEM_PROMPT =
      """
            Ты — классификатор запросов для юридической платформы.
            Определи, является ли сообщение пользователя юридическим вопросом.
            Юридическим считается любой вопрос, связанный с: правом, законами, нормативными актами,
            судебной практикой, договорами, процессуальными действиями, правовым статусом,
            юридической ответственностью, банкротством, корпоративным правом или смежными темами.
            Отвечай строго одним словом: YES или NO.
            """;

  private final LlmClient llmClient;
  private final boolean failOpen;
  private final Cache<String, Boolean> verdictCache;
  private final Counter passCounter;
  private final Counter blockCounter;
  private final Counter failOpenCounter;
  private final Counter cacheHitCounter;

  public LegalDomainGuard(
      LlmClient llmClient,
      @Value("${llm.guard.fail-open:true}") boolean failOpen,
      MeterRegistry registry) {
    this.llmClient = llmClient;
    this.failOpen = failOpen;
    this.verdictCache =
        Caffeine.newBuilder().maximumSize(CACHE_MAX_SIZE).expireAfterWrite(CACHE_TTL).build();
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
    String verdict;
    try {
      LlmResult result =
          llmClient.complete(
              CLASSIFIER_SYSTEM_PROMPT,
              List.of(),
              userMessage,
              LlmOptions.guard(GUARD_MAX_TOKENS, GUARD_TEMPERATURE));
      if (result == null || result.content() == null) {
        log.warn("Guard classifier returned empty response, fail-open={}", failOpen);
        failOpenCounter.increment();
        return failOpen;
      }
      verdict = result.content().trim().toUpperCase();
    } catch (LlmException e) {
      log.warn("Guard classifier unavailable, fail-open={}: {}", failOpen, e.getMessage());
      failOpenCounter.increment();
      return failOpen;
    }

    boolean legal = "YES".equals(verdict);
    verdictCache.put(cacheKey(userMessage), legal);
    log.debug(
        "Guard verdict '{}' for message: {}",
        verdict,
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
