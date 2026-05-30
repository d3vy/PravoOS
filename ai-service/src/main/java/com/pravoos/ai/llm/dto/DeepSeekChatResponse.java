package com.pravoos.ai.llm.dto;

import java.util.List;

public record DeepSeekChatResponse(
        List<Choice> choices
) {
    public record Choice(LlmMessage message) {}

    public String firstContent() {
        if (choices == null || choices.isEmpty()) return "";
        return choices.get(0).message().content();
    }
}
