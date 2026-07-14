package com.pravoos.ai.document.internal.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RerankScoreParserTest {

    private final RerankScoreParser parser = new RerankScoreParser(new ObjectMapper());

    @Test
    void parsesPlainJsonArrayIntoZeroBasedIndexes() {
        Map<Integer, Double> scores = parser.parse("[{\"id\":1,\"score\":9},{\"id\":2,\"score\":3}]", 2);

        assertThat(scores).containsExactlyInAnyOrderEntriesOf(Map.of(0, 9.0, 1, 3.0));
    }

    @Test
    void parsesArrayWrappedInMarkdownFenceAndProse() {
        String response = "Вот оценки:\n```json\n[{\"id\":1,\"score\":7.5}]\n```\nГотово.";

        assertThat(parser.parse(response, 1)).containsExactly(Map.entry(0, 7.5));
    }

    @Test
    void ignoresIndexesOutsideCandidateRange() {
        Map<Integer, Double> scores = parser.parse(
                "[{\"id\":0,\"score\":9},{\"id\":5,\"score\":9},{\"id\":2,\"score\":4}]", 2);

        assertThat(scores).containsExactly(Map.entry(1, 4.0));
    }

    @Test
    void ignoresEntriesWithMissingFields() {
        Map<Integer, Double> scores = parser.parse("[{\"id\":1},{\"score\":5},{\"id\":2,\"score\":6}]", 2);

        assertThat(scores).containsExactly(Map.entry(1, 6.0));
    }

    @Test
    void returnsEmptyMapOnMalformedOrEmptyResponse() {
        assertThat(parser.parse("не могу оценить", 3)).isEmpty();
        assertThat(parser.parse("[{\"id\":1,\"score\":", 3)).isEmpty();
        assertThat(parser.parse("", 3)).isEmpty();
        assertThat(parser.parse(null, 3)).isEmpty();
    }
}
