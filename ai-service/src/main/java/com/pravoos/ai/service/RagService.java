package com.pravoos.ai.service;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RagService {

    private static final String SYSTEM_PROMPT_TEMPLATE = """
            Вы — юридический ИИ-ассистент платформы PravoOS. \
            Вы помогаете юристам, отвечая на их вопросы на основе предоставленных \
            правовых документов и базы знаний.

            Отвечайте на том языке, на котором задан вопрос. \
            Будьте точны и опирайтесь на контекст. \
            Если ответ не содержится в предоставленном контексте — прямо скажите об этом.

            КОНТЕКСТ ИЗ БАЗЫ ЗНАНИЙ:
            %s
            """;

    public String buildSystemPrompt(List<String> relevantChunks) {
        if (relevantChunks.isEmpty()) {
            return String.format(SYSTEM_PROMPT_TEMPLATE, "База знаний пуста.");
        }
        String context = String.join("\n\n---\n\n", relevantChunks);
        return String.format(SYSTEM_PROMPT_TEMPLATE, context);
    }
}
