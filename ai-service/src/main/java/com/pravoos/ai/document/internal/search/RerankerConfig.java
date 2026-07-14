package com.pravoos.ai.document.internal.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pravoos.ai.llm.api.LlmClient;
import com.pravoos.ai.shared.config.HybridSearchProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class RerankerConfig {

    private static final Logger log = LoggerFactory.getLogger(RerankerConfig.class);

    @Bean
    Reranker reranker(HybridSearchProperties properties, LlmClient llmClient, ObjectMapper objectMapper) {
        HybridSearchProperties.Rerank rerank = properties.rerank();
        if (!rerank.enabled()) {
            log.info("LLM reranking disabled — retrieval returns fused hybrid order");
            return new PassThroughReranker();
        }
        return new LlmReranker(
                llmClient, new RerankScoreParser(objectMapper), new PassThroughReranker(), rerank);
    }
}
