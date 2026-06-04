package com.pravoos.ai.service;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RagService {

    private static final String FOLLOW_UP_INSTRUCTION = """

            После ответа выведи на отдельной строке ровно этот разделитель: ##FOLLOWUPS##
            Затем выведи ровно 3 уточняющих вопроса, которые юрист может захотеть задать следующими, \
            по одному на строке, пронумерованных 1. 2. 3.
            """;

    private static final String SYSTEM_PROMPT_TEMPLATE = """
            Вы — юридический ИИ-ассистент платформы PravoOS. \
            Вы помогаете юристам, отвечая на их вопросы на основе предоставленных \
            правовых документов и базы знаний.

            Отвечайте на том языке, на котором задан вопрос. \
            Будьте точны и опирайтесь на контекст. \
            Если ответ не содержится в предоставленном контексте — прямо скажите об этом.

            КОНТЕКСТ ИЗ БАЗЫ ЗНАНИЙ:
            {context}
            """ + FOLLOW_UP_INSTRUCTION;

    private static final String WORKFLOW_PROMPT_TEMPLATE = """
            Вы — юридический ИИ-ассистент платформы PravoOS, специализирующийся на делах о банкротстве. \
            Выполните поставленную задачу, опираясь на документы дела и базу судебной практики.

            ЗАДАЧА:
            {instruction}

            Отвечайте структурированно и со ссылками на нормы права. \
            Опирайтесь только на предоставленный контекст. \
            Если данных в контексте недостаточно для вывода — прямо укажите, какой информации не хватает.

            КОНТЕКСТ (ДОКУМЕНТЫ ДЕЛА И СУДЕБНАЯ ПРАКТИКА):
            {context}
            """ + FOLLOW_UP_INSTRUCTION;

    public String buildSystemPrompt(List<String> relevantChunks) {
        return SYSTEM_PROMPT_TEMPLATE.replace("{context}", joinContext(relevantChunks));
    }

    public String buildWorkflowPrompt(String instruction, List<String> relevantChunks) {
        return WORKFLOW_PROMPT_TEMPLATE
                .replace("{instruction}", instruction)
                .replace("{context}", joinContext(relevantChunks));
    }

    private String joinContext(List<String> relevantChunks) {
        return relevantChunks.isEmpty()
                ? "Контекст пуст."
                : String.join("\n\n---\n\n", relevantChunks);
    }
}
