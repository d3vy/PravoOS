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
            {context}
            """;

    public String buildSystemPrompt(List<String> relevantChunks) {
        String context = relevantChunks.isEmpty()
                ? "База знаний пуста."
                : String.join("\n\n---\n\n", relevantChunks);
        return SYSTEM_PROMPT_TEMPLATE.replace("{context}", context);
    }
}
