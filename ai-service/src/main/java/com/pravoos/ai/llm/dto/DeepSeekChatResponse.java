package com.pravoos.ai.llm.dto;

import java.util.List;

public record DeepSeekChatResponse(
        List<Choice> choices
) {
    public record Choice(LlmMessage message) {}

    public String firstContent() {
        if (choices == null || choices.isEmpty()) return "";
        Choice first = choices.get(0);
        if (first == null || first.message() == null || first.message().content() == null) return "";
        return first.message().content();
    }
}
