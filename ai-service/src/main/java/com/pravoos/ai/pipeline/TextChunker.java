package com.pravoos.ai.pipeline;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
public class TextChunker {

    public List<String> chunk(String text, int chunkSize, int overlap) {
        String[] words = text.trim().split("\\s+");
        List<String> chunks = new ArrayList<>();

        if (words.length == 0) return chunks;

        int step = Math.max(1, chunkSize - overlap);
        for (int i = 0; i < words.length; i += step) {
            int end = Math.min(i + chunkSize, words.length);
            String chunk = String.join(" ", Arrays.copyOfRange(words, i, end));
            if (!chunk.isBlank()) {
                chunks.add(chunk);
            }
            if (end == words.length) break;
        }

        return chunks;
    }
}
