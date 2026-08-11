package com.pravoos.ai.core.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pravoos.ai.core.internal.model.entity.TabularReviewCell;
import com.pravoos.ai.document.api.DocumentChunkMatch;
import com.pravoos.ai.document.api.DocumentChunkMatches;
import com.pravoos.ai.document.api.DocumentRetrieval;
import com.pravoos.ai.llm.api.LlmClient;
import com.pravoos.ai.llm.api.LlmResult;
import com.pravoos.ai.llm.api.LlmUsage;
import com.pravoos.ai.shared.config.TabularReviewProperties;
import com.pravoos.ai.shared.model.enums.ReviewAnswerConfidence;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TabularReviewDocumentProcessorTest {

  private static final UUID REVIEW_ID = UUID.randomUUID();
  private static final UUID DOCUMENT_ID = UUID.randomUUID();
  private static final UUID LAWYER_ID = UUID.randomUUID();
  private static final List<String> QUESTIONS = List.of("Стороны договора", "Срок действия");

  @Mock private DocumentRetrieval documentRetrieval;
  @Mock private LlmClient llmClient;
  @Mock private com.pravoos.ai.shared.service.LlmQuotaService llmQuotaService;
  @Mock private TabularReviewWriter reviewWriter;

  private TabularReviewDocumentProcessor processor;

  @BeforeEach
  void setup() {
    TabularReviewProperties properties = new TabularReviewProperties(10, 8, 4, 20000, 600, 1200);
    processor =
        new TabularReviewDocumentProcessor(
            documentRetrieval,
            new TabularReviewPrompt(),
            llmClient,
            llmQuotaService,
            reviewWriter,
            properties,
            new ObjectMapper());
  }

  @Test
  void mapsSourceNumbersToRetrievedFragments() {
    stubRetrieval(
        new DocumentChunkMatch(UUID.randomUUID(), 4, "Стороны: ООО «Альфа» и ИП Петров", 0.9),
        new DocumentChunkMatch(UUID.randomUUID(), 11, "Договор действует до 31.12.2026", 0.8));
    stubCompletion(
        """
                {"answers":[
                  {"question":1,"answer":"ООО «Альфа» и ИП Петров","confidence":"HIGH","sources":[1]},
                  {"question":2,"answer":"До 31.12.2026","confidence":"MEDIUM","sources":[2]}
                ]}""");

    assertThat(process()).isTrue();

    verify(llmQuotaService).recordTokenUsage(LAWYER_ID, 128L);
    List<TabularReviewCell> cells = savedCells();
    assertThat(cells).hasSize(2);
    assertThat(cells.get(0).getConfidence()).isEqualTo(ReviewAnswerConfidence.HIGH);
    assertThat(cells.get(0).getCitations())
        .singleElement()
        .satisfies(
            citation -> {
              assertThat(citation.chunkIndex()).isEqualTo(4);
              assertThat(citation.quote()).contains("ООО «Альфа»");
            });
    assertThat(cells.get(1).getCitations())
        .singleElement()
        .satisfies(citation -> assertThat(citation.chunkIndex()).isEqualTo(11));
  }

  @Test
  void sendsDocumentContentInUserMessageNotSystemPrompt() {
    stubRetrieval(
        new DocumentChunkMatch(UUID.randomUUID(), 4, "Стороны: ООО «Альфа» и ИП Петров", 0.9));
    stubCompletion(
        """
                {"answers":[{"question":1,"answer":"Ответ","confidence":"HIGH","sources":[1]}]}""");

    process();

    ArgumentCaptor<String> systemPrompt = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> userMessage = ArgumentCaptor.forClass(String.class);
    verify(llmClient).complete(systemPrompt.capture(), any(), userMessage.capture());
    assertThat(systemPrompt.getValue()).doesNotContain("ООО «Альфа»", "Договор поставки");
    assertThat(userMessage.getValue())
        .contains("ООО «Альфа»", "Договор поставки", "Стороны договора");
  }

  @Test
  void dropsSourceNumbersOutsideFragmentRange() {
    stubRetrieval(new DocumentChunkMatch(UUID.randomUUID(), 0, "Единственный фрагмент", 0.7));
    stubCompletion(
        """
                {"answers":[
                  {"question":1,"answer":"Ответ","confidence":"HIGH","sources":[1,7,0,-2]},
                  {"question":2,"answer":"Ответ","confidence":"LOW","sources":[]}
                ]}""");

    process();

    assertThat(savedCells().get(0).getCitations()).hasSize(1);
    assertThat(savedCells().get(1).getCitations()).isEmpty();
  }

  @Test
  void missingAnswerForQuestionBecomesNotFoundCell() {
    stubRetrieval(new DocumentChunkMatch(UUID.randomUUID(), 2, "Текст", 0.6));
    stubCompletion(
        """
                {"answers":[{"question":1,"answer":"Ответ","confidence":"HIGH","sources":[1]}]}""");

    process();

    List<TabularReviewCell> cells = savedCells();
    assertThat(cells.get(1).getConfidence()).isEqualTo(ReviewAnswerConfidence.NOT_FOUND);
    assertThat(cells.get(1).getCitations()).isEmpty();
  }

  @Test
  void skipsLlmCallWhenNothingRetrieved() {
    when(documentRetrieval.retrieveInDocument(anyList(), anyInt(), eq(DOCUMENT_ID)))
        .thenReturn(DocumentChunkMatches.empty());

    assertThat(process()).isTrue();

    verify(llmClient, never()).complete(anyString(), any(), anyString());
    assertThat(savedCells())
        .allSatisfy(
            cell -> assertThat(cell.getConfidence()).isEqualTo(ReviewAnswerConfidence.NOT_FOUND));
  }

  @Test
  void exceededQuotaFailsDocumentBeforeSpendingRetrieval() {
    org.mockito.Mockito.doThrow(new com.pravoos.ai.shared.exception.LlmQuotaExceededException())
        .when(llmQuotaService)
        .assertWithinQuota(LAWYER_ID);

    assertThat(process()).isFalse();

    verify(documentRetrieval, never()).retrieveInDocument(anyList(), anyInt(), any());
    verify(llmClient, never()).complete(anyString(), any(), anyString());
    verify(llmQuotaService, never()).recordUsage(any(), anyLong());
    verify(reviewWriter).markDocumentFailed(eq(REVIEW_ID), eq(DOCUMENT_ID), anyString());
    verify(reviewWriter, never()).saveDocumentResult(any(), any(), any());
  }

  @Test
  void extractsJsonWrappedInCodeFenceWithSurroundingProse() {
    stubRetrieval(new DocumentChunkMatch(UUID.randomUUID(), 3, "Текст {черновой}", 0.7));
    stubCompletion(
        """
                Готово. Вот результат (формат {answers}):
                ```json
                {"answers":[{"question":1,"answer":"Итог","confidence":"HIGH","sources":[1]}]}
                ```
                Спасибо! {конец}""");

    assertThat(process()).isTrue();

    List<TabularReviewCell> cells = savedCells();
    assertThat(cells.get(0).getAnswer()).isEqualTo("Итог");
    assertThat(cells.get(0).getConfidence()).isEqualTo(ReviewAnswerConfidence.HIGH);
  }

  @Test
  void malformedModelOutputMarksDocumentFailed() {
    stubRetrieval(new DocumentChunkMatch(UUID.randomUUID(), 1, "Текст", 0.5));
    stubCompletion("модель ответила прозой без json");

    assertThat(process()).isFalse();

    verify(reviewWriter).markDocumentFailed(eq(REVIEW_ID), eq(DOCUMENT_ID), anyString());
    verify(reviewWriter, never()).saveDocumentResult(any(), any(), any());
  }

  private boolean process() {
    return processor.process(REVIEW_ID, DOCUMENT_ID, "Договор поставки", QUESTIONS, LAWYER_ID);
  }

  private void stubRetrieval(DocumentChunkMatch... matches) {
    when(documentRetrieval.retrieveInDocument(anyList(), anyInt(), eq(DOCUMENT_ID)))
        .thenReturn(new DocumentChunkMatches(List.of(matches), 128L, 0L));
  }

  private void stubCompletion(String content) {
    when(llmClient.complete(anyString(), any(), anyString()))
        .thenReturn(new LlmResult(content, new LlmUsage(10, 20, 30)));
    doNothingOnQuota();
  }

  private void doNothingOnQuota() {
    org.mockito.Mockito.lenient().doNothing().when(llmQuotaService).recordUsage(any(), anyLong());
  }

  @SuppressWarnings("unchecked")
  private List<TabularReviewCell> savedCells() {
    ArgumentCaptor<List<TabularReviewCell>> captor = ArgumentCaptor.forClass(List.class);
    verify(reviewWriter).saveDocumentResult(eq(REVIEW_ID), eq(DOCUMENT_ID), captor.capture());
    return captor.getValue();
  }
}
