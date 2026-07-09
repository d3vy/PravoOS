package com.pravoos.llm.web.dto;

import com.pravoos.llm.domain.LlmMessage;
import com.pravoos.llm.domain.LlmOptions;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CompleteRequest(
        String systemPrompt,
        List<LlmMessage> history,
        @NotNull String userMessage,
        LlmOptions options
) {}
