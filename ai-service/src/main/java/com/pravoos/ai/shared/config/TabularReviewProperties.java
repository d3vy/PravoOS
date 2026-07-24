package com.pravoos.ai.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tabular-review")
public record TabularReviewProperties(
    int maxDocuments,
    int maxQuestions,
    int topKPerQuestion,
    int contextMaxChars,
    int answerMaxChars,
    int quoteMaxChars) {}
