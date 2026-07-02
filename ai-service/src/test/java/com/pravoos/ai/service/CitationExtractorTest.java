package com.pravoos.ai.service;

import com.pravoos.ai.model.enums.CitationType;
import com.pravoos.ai.service.CitationExtractor.ExtractedCitation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CitationExtractorTest {

    private final CitationExtractor extractor = new CitationExtractor(new LegalActRegistry());

    @Test
    void extractsCourtCaseNumbers() {
        List<ExtractedCitation> citations = extractor.extract(
                "См. дело А40-12345/2024 и определение по делу А56-9999/2023.", 100);

        assertThat(citations)
                .filteredOn(c -> c.type() == CitationType.COURT_CASE)
                .extracting(ExtractedCitation::core)
                .containsExactlyInAnyOrder("А40-12345/2024", "А56-9999/2023");
    }

    @Test
    void deduplicatesRepeatedCitations() {
        List<ExtractedCitation> citations = extractor.extract(
                "Дело А40-1/2024, снова А40-1/2024 и ещё раз а40-1/2024.", 100);

        assertThat(citations).filteredOn(c -> c.type() == CitationType.COURT_CASE).hasSize(1);
    }

    @Test
    void extractsStatuteWithRecognizedAct() {
        List<ExtractedCitation> citations = extractor.extract(
                "Согласно ст. 61.2 Закона о банкротстве сделка оспорима.", 100);

        assertThat(citations)
                .filteredOn(c -> c.type() == CitationType.STATUTE)
                .singleElement()
                .satisfies(c -> {
                    assertThat(c.core()).isEqualTo("61.2");
                    assertThat(c.actCanonical()).contains("127-ФЗ");
                });
    }

    @Test
    void extractsStatuteWithoutRecognizedAct() {
        List<ExtractedCitation> citations = extractor.extract("Нарушена ст. 42 некоего акта.", 100);

        assertThat(citations)
                .filteredOn(c -> c.type() == CitationType.STATUTE)
                .singleElement()
                .satisfies(c -> {
                    assertThat(c.core()).isEqualTo("42");
                    assertThat(c.actCanonical()).isNull();
                });
    }

    @Test
    void respectsLimit() {
        List<ExtractedCitation> citations = extractor.extract(
                "А40-1/2024 А40-2/2024 А40-3/2024", 2);

        assertThat(citations).hasSize(2);
    }
}
