package com.pravoos.ai.core.internal.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.pravoos.ai.core.internal.dto.DiffChange;
import com.pravoos.ai.core.internal.dto.DiffSegment;
import com.pravoos.ai.shared.model.enums.DiffChangeType;
import com.pravoos.ai.shared.model.enums.DiffSegmentType;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TextDiffServiceTest {

  private TextDiffService textDiffService;

  @BeforeEach
  void setup() {
    textDiffService = new TextDiffService();
  }

  @Test
  void identicalTextsProduceNoChanges() {
    String text = "Пункт 1. Оплата.\nПункт 2. Сроки.";
    assertThat(textDiffService.diff(text, text)).isEmpty();
  }

  @Test
  void whitespaceOnlyDifferenceIsIgnored() {
    List<DiffChange> changes = textDiffService.diff("Пункт   1.  Оплата.", "Пункт 1. Оплата.");
    assertThat(changes).isEmpty();
  }

  @Test
  void insertedParagraphIsAdded() {
    List<DiffChange> changes =
        textDiffService.diff("Пункт 1.\nПункт 3.", "Пункт 1.\nПункт 2.\nПункт 3.");
    assertThat(changes).hasSize(1);
    assertThat(changes.get(0).type()).isEqualTo(DiffChangeType.ADDED);
    assertThat(changes.get(0).revisedText()).contains("Пункт 2.");
    assertThat(changes.get(0).baseText()).isEmpty();
  }

  @Test
  void removedParagraphIsRemoved() {
    List<DiffChange> changes =
        textDiffService.diff("Пункт 1.\nПункт 2.\nПункт 3.", "Пункт 1.\nПункт 3.");
    assertThat(changes).hasSize(1);
    assertThat(changes.get(0).type()).isEqualTo(DiffChangeType.REMOVED);
    assertThat(changes.get(0).baseText()).contains("Пункт 2.");
  }

  @Test
  void replacedParagraphIsModified() {
    List<DiffChange> changes =
        textDiffService.diff("Срок оплаты — 30 дней.", "Срок оплаты — 10 дней.");
    assertThat(changes).hasSize(1);
    assertThat(changes.get(0).type()).isEqualTo(DiffChangeType.MODIFIED);
    assertThat(changes.get(0).baseText()).contains("30 дней");
    assertThat(changes.get(0).revisedText()).contains("10 дней");
  }

  @Test
  void changesAreOrderedSequentially() {
    List<DiffChange> changes = textDiffService.diff("A\nB\nC", "A\nX\nC\nD");
    assertThat(changes).hasSize(2);
    assertThat(changes.get(0).order()).isEqualTo(0);
    assertThat(changes.get(1).order()).isEqualTo(1);
  }

  @Test
  void inlineSegmentsIsolateChangedWords() {
    List<DiffSegment> segments =
        textDiffService.inlineSegments("Срок оплаты — 30 дней.", "Срок оплаты — 10 дней.");

    assertThat(segments)
        .extracting(DiffSegment::type)
        .containsExactly(
            DiffSegmentType.EQUAL,
            DiffSegmentType.REMOVED,
            DiffSegmentType.ADDED,
            DiffSegmentType.EQUAL);
    assertThat(segments.get(1).text()).contains("30");
    assertThat(segments.get(2).text()).contains("10");
  }

  @Test
  void inlineSegmentsConcatenateBackToOriginalTexts() {
    String baseText = "Заказчик оплачивает работы в течение 30 дней.";
    String revisedText = "Заказчик оплачивает услуги в течение 10 банковских дней.";
    List<DiffSegment> segments = textDiffService.inlineSegments(baseText, revisedText);

    assertThat(rebuild(segments, DiffSegmentType.ADDED)).isEqualTo(baseText);
    assertThat(rebuild(segments, DiffSegmentType.REMOVED)).isEqualTo(revisedText);
  }

  @Test
  void inlineSegmentsAreEmptyForOversizedBlocks() {
    String longText = "слово ".repeat(500);
    assertThat(textDiffService.inlineSegments(longText, longText + "хвост")).isEmpty();
  }

  @Test
  void inlineSegmentsAreEmptyWhenOneSideIsBlank() {
    assertThat(textDiffService.inlineSegments("", "Новый пункт.")).isEmpty();
  }

  private String rebuild(List<DiffSegment> segments, DiffSegmentType excluded) {
    return segments.stream()
        .filter(segment -> segment.type() != excluded)
        .map(DiffSegment::text)
        .collect(Collectors.joining());
  }
}
