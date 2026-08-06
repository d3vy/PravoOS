package com.pravoos.ai.document.internal.search;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RerankPromptTest {

  @Test
  void systemPromptDescribesScoringScaleAndOutputFormat() {
    assertThat(RerankPrompt.SYSTEM_PROMPT).contains("от 0 до 10");
    assertThat(RerankPrompt.SYSTEM_PROMPT).contains("[{\"id\":1,\"score\":8}");
  }

  @Test
  void buildUserMessageNumbersCandidatesFromOneRegardlessOfChunkIndex() {
    ChunkCandidate first = legislationCandidate(7, "10", "ГК РФ");
    ChunkCandidate second = legislationCandidate(3, "20", "ГК РФ");

    String result = RerankPrompt.buildUserMessage("вопрос", List.of(first, second), 1000);

    assertThat(result).contains("[id=1]");
    assertThat(result).contains("[id=2]");
    assertThat(result).doesNotContain("[id=7]");
    assertThat(result).doesNotContain("[id=3]");
  }

  @Test
  void includesQueryAtTopOfMessage() {
    String result = RerankPrompt.buildUserMessage("Какой срок исковой давности?", List.of(), 1000);

    assertThat(result).startsWith("Вопрос юриста:\nКакой срок исковой давности?");
  }

  @Test
  void sourceLabelForLegislationCombinesArticleAndAct() {
    ChunkCandidate candidate = legislationCandidate(1, "15", "ГК РФ");

    String result = RerankPrompt.buildUserMessage("вопрос", List.of(candidate), 1000);

    assertThat(result).contains("[id=1] ст. 15 ГК РФ");
  }

  @Test
  void sourceLabelFallsBackToDocumentTitleWhenNotLegislation() {
    ChunkCandidate candidate =
        new ChunkCandidate(
            UUID.randomUUID(), 0, "содержимое", "Договор аренды", false, null, null, null, 0);

    String result = RerankPrompt.buildUserMessage("вопрос", List.of(candidate), 1000);

    assertThat(result).contains("[id=1] Договор аренды");
  }

  @Test
  void sourceLabelIsEmptyWhenNoTitleAndNotLegislation() {
    ChunkCandidate candidate =
        new ChunkCandidate(UUID.randomUUID(), 0, "содержимое", null, false, null, null, null, 0);

    String result = RerankPrompt.buildUserMessage("вопрос", List.of(candidate), 1000);

    assertThat(result).contains("[id=1] \n");
  }

  @Test
  void truncatesContentLongerThanMaxCharsAndAppendsEllipsis() {
    ChunkCandidate candidate =
        new ChunkCandidate(
            UUID.randomUUID(), 0, "0123456789", "Документ", false, null, null, null, 0);

    String result = RerankPrompt.buildUserMessage("вопрос", List.of(candidate), 5);

    assertThat(result).contains("01234...");
    assertThat(result).doesNotContain("0123456789");
  }

  @Test
  void doesNotTruncateContentShorterThanOrEqualToMaxChars() {
    ChunkCandidate candidate =
        new ChunkCandidate(UUID.randomUUID(), 0, "12345", "Документ", false, null, null, null, 0);

    String result = RerankPrompt.buildUserMessage("вопрос", List.of(candidate), 5);

    assertThat(result).contains("12345\n\n");
    assertThat(result).doesNotContain("12345...");
  }

  @Test
  void nullContentIsTreatedAsEmptyString() {
    ChunkCandidate candidate =
        new ChunkCandidate(UUID.randomUUID(), 0, null, "Документ", false, null, null, null, 0);

    String result = RerankPrompt.buildUserMessage("вопрос", List.of(candidate), 5);

    assertThat(result).contains("[id=1] Документ\n\n\n");
  }

  private static ChunkCandidate legislationCandidate(int chunkIndex, String article, String act) {
    return new ChunkCandidate(
        UUID.randomUUID(),
        chunkIndex,
        "содержимое статьи",
        null,
        true,
        act,
        article,
        LocalDate.of(2020, 1, 1),
        0);
  }
}
