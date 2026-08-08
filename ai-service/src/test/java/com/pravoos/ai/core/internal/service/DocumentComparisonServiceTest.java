package com.pravoos.ai.core.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pravoos.ai.core.api.CaseAccessProvider;
import com.pravoos.ai.core.internal.dto.DiffChange;
import com.pravoos.ai.core.internal.dto.DocumentComparisonDto;
import com.pravoos.ai.core.internal.model.entity.DocumentComparison;
import com.pravoos.ai.core.internal.repository.jpa.DocumentComparisonRepository;
import com.pravoos.ai.document.api.DocumentAccess;
import com.pravoos.ai.document.api.DocumentRef;
import com.pravoos.ai.llm.api.LlmClient;
import com.pravoos.ai.llm.api.LlmResult;
import com.pravoos.ai.llm.api.LlmUsage;
import com.pravoos.ai.shared.config.DocumentComparisonProperties;
import com.pravoos.ai.shared.exception.ContractReviewFailedException;
import com.pravoos.ai.shared.exception.DocumentComparisonNotFoundException;
import com.pravoos.ai.shared.exception.DocumentNotFoundException;
import com.pravoos.ai.shared.model.enums.ContractRiskLevel;
import com.pravoos.ai.shared.model.enums.DiffChangeType;
import com.pravoos.ai.shared.service.LlmQuotaService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DocumentComparisonServiceTest {

  @Mock private CaseAccessProvider caseAccessProvider;
  @Mock private DocumentAccess documentAccess;
  @Mock private TextDiffService textDiffService;
  @Mock private DocumentComparisonPrompt comparisonPrompt;
  @Mock private LlmClient llmClient;
  @Mock private LlmQuotaService llmQuotaService;
  @Mock private DocumentComparisonRepository comparisonRepository;

  private DocumentComparisonService service;

  private final UUID caseId = UUID.randomUUID();
  private final UUID lawyerId = UUID.randomUUID();
  private final UUID baseDocumentId = UUID.randomUUID();
  private final UUID revisedDocumentId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    service =
        new DocumentComparisonService(
            caseAccessProvider,
            documentAccess,
            textDiffService,
            comparisonPrompt,
            llmClient,
            llmQuotaService,
            comparisonRepository,
            new DocumentComparisonProperties(40000, 30, 2000),
            new ObjectMapper());
  }

  private DocumentRef baseRef() {
    return new DocumentRef(baseDocumentId, caseId, lawyerId, "Договор v1");
  }

  private DocumentRef revisedRef() {
    return new DocumentRef(revisedDocumentId, caseId, lawyerId, "Договор v2");
  }

  @Test
  void compareRejectsSameDocumentId() {
    assertThatThrownBy(() -> service.compare(baseDocumentId, baseDocumentId, lawyerId, List.of()))
        .isInstanceOf(ContractReviewFailedException.class);

    verify(llmQuotaService, never()).assertWithinQuota(any());
  }

  @Test
  void compareRejectsNonCaseDocument() {
    when(documentAccess.findForReview(baseDocumentId))
        .thenReturn(new DocumentRef(baseDocumentId, null, lawyerId, "Заметка"));

    assertThatThrownBy(
            () -> service.compare(baseDocumentId, revisedDocumentId, lawyerId, List.of()))
        .isInstanceOf(DocumentNotFoundException.class);

    verify(comparisonRepository, never()).save(any());
  }

  @Test
  void compareRejectsDocumentsFromDifferentCases() {
    when(documentAccess.findForReview(baseDocumentId)).thenReturn(baseRef());
    when(documentAccess.findForReview(revisedDocumentId))
        .thenReturn(new DocumentRef(revisedDocumentId, UUID.randomUUID(), lawyerId, "Другое дело"));

    assertThatThrownBy(
            () -> service.compare(baseDocumentId, revisedDocumentId, lawyerId, List.of()))
        .isInstanceOf(ContractReviewFailedException.class);

    verify(comparisonRepository, never()).save(any());
  }

  @Test
  void compareChecksVisibilityForBothDocuments() {
    when(documentAccess.findForReview(baseDocumentId)).thenReturn(baseRef());
    when(documentAccess.findForReview(revisedDocumentId)).thenReturn(revisedRef());
    when(documentAccess.extractText(baseDocumentId)).thenReturn("текст 1");
    when(documentAccess.extractText(revisedDocumentId)).thenReturn("текст 2");
    when(textDiffService.diff("текст 1", "текст 2")).thenReturn(List.of());
    when(comparisonRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    service.compare(baseDocumentId, revisedDocumentId, lawyerId, List.of());

    verify(caseAccessProvider, org.mockito.Mockito.times(2))
        .assertCaseVisible(caseId, lawyerId, List.of());
  }

  @Test
  void compareReturnsIdenticalResultWhenNoTextualChanges() {
    when(documentAccess.findForReview(baseDocumentId)).thenReturn(baseRef());
    when(documentAccess.findForReview(revisedDocumentId)).thenReturn(revisedRef());
    when(documentAccess.extractText(baseDocumentId)).thenReturn("тот же текст");
    when(documentAccess.extractText(revisedDocumentId)).thenReturn("тот же текст");
    when(textDiffService.diff("тот же текст", "тот же текст")).thenReturn(List.of());
    when(comparisonRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    DocumentComparisonDto dto =
        service.compare(baseDocumentId, revisedDocumentId, lawyerId, List.of());

    assertThat(dto.changeCount()).isZero();
    assertThat(dto.riskScore()).isZero();
    assertThat(dto.summary()).contains("идентичны");
    verify(llmClient, never()).complete(any(), any(), any());
  }

  @Test
  void compareRejectsBlankExtractedText() {
    when(documentAccess.findForReview(baseDocumentId)).thenReturn(baseRef());
    when(documentAccess.findForReview(revisedDocumentId)).thenReturn(revisedRef());
    when(documentAccess.extractText(baseDocumentId)).thenReturn("  ");

    assertThatThrownBy(
            () -> service.compare(baseDocumentId, revisedDocumentId, lawyerId, List.of()))
        .isInstanceOf(ContractReviewFailedException.class);
  }

  @Test
  void compareParsesLlmAssessmentAndPersistsChanges() {
    when(documentAccess.findForReview(baseDocumentId)).thenReturn(baseRef());
    when(documentAccess.findForReview(revisedDocumentId)).thenReturn(revisedRef());
    when(documentAccess.extractText(baseDocumentId)).thenReturn("старый текст");
    when(documentAccess.extractText(revisedDocumentId)).thenReturn("новый текст");
    DiffChange rawChange =
        new DiffChange(
            1, DiffChangeType.MODIFIED, "старый текст", "новый текст", null, null, List.of());
    when(textDiffService.diff("старый текст", "новый текст")).thenReturn(List.of(rawChange));
    when(comparisonPrompt.buildSystemPrompt(any())).thenReturn("system prompt");
    when(llmClient.complete(eq("system prompt"), eq(List.of()), any()))
        .thenReturn(
            new LlmResult(
                """
                {"summary":"Риск повышен","riskScore":75,"changes":[\
                {"index":1,"level":"HIGH","comment":"Существенное изменение суммы"}]}\
                """,
                new LlmUsage(100, 50, 150)));
    when(comparisonRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    DocumentComparisonDto dto =
        service.compare(baseDocumentId, revisedDocumentId, lawyerId, List.of());

    assertThat(dto.changeCount()).isEqualTo(1);
    assertThat(dto.riskScore()).isEqualTo((short) 75);
    assertThat(dto.summary()).isEqualTo("Риск повышен");
    assertThat(dto.highRiskCount()).isEqualTo(1);
    assertThat(dto.changes().get(0).riskLevel()).isEqualTo(ContractRiskLevel.HIGH);
    verify(llmQuotaService).recordUsage(lawyerId, 150L);
  }

  @Test
  void compareClampsOutOfRangeRiskScore() {
    when(documentAccess.findForReview(baseDocumentId)).thenReturn(baseRef());
    when(documentAccess.findForReview(revisedDocumentId)).thenReturn(revisedRef());
    when(documentAccess.extractText(baseDocumentId)).thenReturn("a");
    when(documentAccess.extractText(revisedDocumentId)).thenReturn("b");
    DiffChange rawChange = new DiffChange(1, DiffChangeType.ADDED, "a", "b", null, null, List.of());
    when(textDiffService.diff("a", "b")).thenReturn(List.of(rawChange));
    when(comparisonPrompt.buildSystemPrompt(any())).thenReturn("system prompt");
    when(llmClient.complete(any(), any(), any()))
        .thenReturn(
            new LlmResult("{\"summary\":\"ok\",\"riskScore\":500,\"changes\":[]}", LlmUsage.EMPTY));
    when(comparisonRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    DocumentComparisonDto dto =
        service.compare(baseDocumentId, revisedDocumentId, lawyerId, List.of());

    assertThat(dto.riskScore()).isEqualTo((short) 100);
  }

  @Test
  void compareThrowsWhenLlmReturnsEmptyContent() {
    when(documentAccess.findForReview(baseDocumentId)).thenReturn(baseRef());
    when(documentAccess.findForReview(revisedDocumentId)).thenReturn(revisedRef());
    when(documentAccess.extractText(baseDocumentId)).thenReturn("a");
    when(documentAccess.extractText(revisedDocumentId)).thenReturn("b");
    DiffChange rawChange = new DiffChange(1, DiffChangeType.ADDED, "a", "b", null, null, List.of());
    when(textDiffService.diff("a", "b")).thenReturn(List.of(rawChange));
    when(comparisonPrompt.buildSystemPrompt(any())).thenReturn("system prompt");
    when(llmClient.complete(any(), any(), any())).thenReturn(new LlmResult(null, LlmUsage.EMPTY));

    assertThatThrownBy(
            () -> service.compare(baseDocumentId, revisedDocumentId, lawyerId, List.of()))
        .isInstanceOf(ContractReviewFailedException.class);
  }

  @Test
  void compareThrowsWhenLlmReturnsNonJsonContent() {
    when(documentAccess.findForReview(baseDocumentId)).thenReturn(baseRef());
    when(documentAccess.findForReview(revisedDocumentId)).thenReturn(revisedRef());
    when(documentAccess.extractText(baseDocumentId)).thenReturn("a");
    when(documentAccess.extractText(revisedDocumentId)).thenReturn("b");
    DiffChange rawChange = new DiffChange(1, DiffChangeType.ADDED, "a", "b", null, null, List.of());
    when(textDiffService.diff("a", "b")).thenReturn(List.of(rawChange));
    when(comparisonPrompt.buildSystemPrompt(any())).thenReturn("system prompt");
    when(llmClient.complete(any(), any(), any()))
        .thenReturn(new LlmResult("прошу прощения, не могу помочь", LlmUsage.EMPTY));

    assertThatThrownBy(
            () -> service.compare(baseDocumentId, revisedDocumentId, lawyerId, List.of()))
        .isInstanceOf(ContractReviewFailedException.class);
  }

  @Test
  void findByCaseChecksVisibilityAndMaps() {
    DocumentComparison comparison = persistedComparison();
    when(comparisonRepository.findByCaseIdOrderByCreatedAtDesc(caseId))
        .thenReturn(List.of(comparison));

    List<DocumentComparisonDto> result = service.findByCase(caseId, lawyerId, List.of());

    verify(caseAccessProvider).assertCaseVisible(caseId, lawyerId, List.of());
    assertThat(result).hasSize(1);
    assertThat(result.get(0).id()).isEqualTo(comparison.getId());
  }

  @Test
  void getThrowsWhenComparisonDoesNotExist() {
    UUID comparisonId = UUID.randomUUID();
    when(comparisonRepository.findById(comparisonId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.get(comparisonId, lawyerId, List.of()))
        .isInstanceOf(DocumentComparisonNotFoundException.class);
  }

  @Test
  void getChecksVisibilityAndReturnsExistingComparison() {
    DocumentComparison comparison = persistedComparison();
    when(comparisonRepository.findById(comparison.getId())).thenReturn(Optional.of(comparison));

    DocumentComparisonDto dto = service.get(comparison.getId(), lawyerId, List.of());

    verify(caseAccessProvider).assertCaseVisible(caseId, lawyerId, List.of());
    assertThat(dto.id()).isEqualTo(comparison.getId());
  }

  private DocumentComparison persistedComparison() {
    DocumentComparison comparison = new DocumentComparison();
    setId(comparison, UUID.randomUUID());
    comparison.setCaseId(caseId);
    comparison.setBaseDocumentId(baseDocumentId);
    comparison.setRevisedDocumentId(revisedDocumentId);
    comparison.setLawyerId(lawyerId);
    comparison.setBaseDocumentTitle("v1");
    comparison.setRevisedDocumentTitle("v2");
    comparison.setSummary("Резюме");
    comparison.setRiskScore((short) 10);
    comparison.setChangeCount(0);
    comparison.setHighRiskCount(0);
    comparison.setChanges(List.of());
    return comparison;
  }

  private void setId(DocumentComparison comparison, UUID id) {
    try {
      java.lang.reflect.Field field = DocumentComparison.class.getDeclaredField("id");
      field.setAccessible(true);
      field.set(comparison, id);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }
}
