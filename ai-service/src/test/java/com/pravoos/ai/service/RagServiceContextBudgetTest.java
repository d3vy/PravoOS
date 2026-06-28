package com.pravoos.ai.service;

import com.pravoos.ai.config.DocumentProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RagServiceContextBudgetTest {

    private RagService ragService(int contextMaxChars) {
        return new RagService(new DocumentProperties(null, 512, 50, 5, contextMaxChars));
    }

    @Test
    void keepsContextWithinCharBudget() {
        String chunk = "ю".repeat(1000);
        String prompt = ragService(2500).buildSystemPrompt(List.of(chunk, chunk, chunk, chunk));

        assertThat(prompt).contains("ю");
        assertThat(prompt.length()).isLessThan(4000);
    }

    @Test
    void prioritizesEarlierChunksWhenTrimming() {
        String first = "ПЕРВЫЙ".repeat(100);
        String second = "ВТОРОЙ".repeat(100);
        String third = "ТРЕТИЙ".repeat(100);
        String prompt = ragService(700).buildSystemPrompt(List.of(first, second, third));

        assertThat(prompt).contains("ПЕРВЫЙ");
        assertThat(prompt).doesNotContain("ТРЕТИЙ");
    }

    @Test
    void emptyContextRendersPlaceholder() {
        assertThat(ragService(24000).buildSystemPrompt(List.of())).contains("Контекст пуст.");
    }
}
