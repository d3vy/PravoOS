package com.pravoos.ai.core.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pravoos.ai.core.api.CaseAccessProvider;
import com.pravoos.ai.core.internal.dto.CreateTabularReviewRequest;
import com.pravoos.ai.core.internal.dto.TabularReviewDto;
import com.pravoos.ai.core.internal.dto.TabularReviewSummaryDto;
import com.pravoos.ai.core.internal.model.entity.TabularReview;
import com.pravoos.ai.core.internal.model.entity.TabularReviewDocument;
import com.pravoos.ai.core.internal.repository.jpa.TabularReviewCellRepository;
import com.pravoos.ai.core.internal.repository.jpa.TabularReviewDocumentRepository;
import com.pravoos.ai.core.internal.repository.jpa.TabularReviewRepository;
import com.pravoos.ai.document.api.DocumentAccess;
import com.pravoos.ai.document.api.DocumentRef;
import com.pravoos.ai.shared.config.TabularReviewProperties;
import com.pravoos.ai.shared.exception.DocumentNotFoundException;
import com.pravoos.ai.shared.exception.TabularReviewFailedException;
import com.pravoos.ai.shared.exception.TabularReviewNotFoundException;
import com.pravoos.ai.shared.model.enums.TabularReviewStatus;
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
class TabularReviewServiceTest {

  @Mock private CaseAccessProvider caseAccessProvider;
  @Mock private DocumentAccess documentAccess;
  @Mock private LlmQuotaService llmQuotaService;
  @Mock private TabularReviewRepository reviewRepository;
  @Mock private TabularReviewDocumentRepository reviewDocumentRepository;
  @Mock private TabularReviewCellRepository cellRepository;
  @Mock private TabularReviewRunner reviewRunner;

  private TabularReviewService service;

  private final UUID caseId = UUID.randomUUID();
  private final UUID lawyerId = UUID.randomUUID();
  private final UUID documentId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    service =
        new TabularReviewService(
            caseAccessProvider,
            documentAccess,
            llmQuotaService,
            reviewRepository,
            reviewDocumentRepository,
            cellRepository,
            reviewRunner,
            new TabularReviewProperties(2, 2, 4, 40000, 2000, 500));
  }

  private CreateTabularReviewRequest request(List<UUID> documentIds, List<String> questions) {
    return new CreateTabularReviewRequest(caseId, "Разбор", documentIds, questions);
  }

  @Test
  void createRejectsEmptyQuestionsAfterNormalization() {
    assertThatThrownBy(
            () -> service.create(request(List.of(documentId), List.of("   ")), lawyerId, List.of()))
        .isInstanceOf(TabularReviewFailedException.class);

    verify(reviewRepository, never()).save(any());
  }

  @Test
  void createRejectsTooManyQuestions() {
    assertThatThrownBy(
            () ->
                service.create(
                    request(List.of(documentId), List.of("Кто стороны?", "Сумма?", "Срок?")),
                    lawyerId,
                    List.of()))
        .isInstanceOf(TabularReviewFailedException.class);
  }

  @Test
  void createRejectsTooManyDocuments() {
    assertThatThrownBy(
            () ->
                service.create(
                    request(
                        List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()),
                        List.of("Кто стороны?")),
                    lawyerId,
                    List.of()))
        .isInstanceOf(TabularReviewFailedException.class);
  }

  @Test
  void createRejectsDocumentNotBelongingToAnyCase() {
    when(documentAccess.findForReview(documentId))
        .thenReturn(new DocumentRef(documentId, null, lawyerId, "Заметка"));

    assertThatThrownBy(
            () ->
                service.create(
                    request(List.of(documentId), List.of("Кто стороны?")), lawyerId, List.of()))
        .isInstanceOf(DocumentNotFoundException.class);
  }

  @Test
  void createRejectsDocumentFromDifferentCase() {
    when(documentAccess.findForReview(documentId))
        .thenReturn(new DocumentRef(documentId, UUID.randomUUID(), lawyerId, "Другое дело"));

    assertThatThrownBy(
            () ->
                service.create(
                    request(List.of(documentId), List.of("Кто стороны?")), lawyerId, List.of()))
        .isInstanceOf(TabularReviewFailedException.class);
  }

  @Test
  void createDeduplicatesRepeatedDocumentIds() {
    when(documentAccess.findForReview(documentId))
        .thenReturn(new DocumentRef(documentId, caseId, lawyerId, "Договор"));
    when(reviewRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    TabularReviewDto dto =
        service.create(
            request(List.of(documentId, documentId), List.of("Кто стороны?")), lawyerId, List.of());

    assertThat(dto.documents()).hasSize(1);
    verify(documentAccess).findForReview(documentId);
  }

  @Test
  void createUsesDefaultTitleWhenBlank() {
    when(documentAccess.findForReview(documentId))
        .thenReturn(new DocumentRef(documentId, caseId, lawyerId, "Договор"));
    when(reviewRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    TabularReviewDto dto =
        service.create(
            new CreateTabularReviewRequest(caseId, "  ", List.of(documentId), List.of("Вопрос?")),
            lawyerId,
            List.of());

    assertThat(dto.title()).isEqualTo("Табличный разбор");
  }

  @Test
  void createDispatchesRunnerImmediatelyWithoutActiveTransaction() {
    when(documentAccess.findForReview(documentId))
        .thenReturn(new DocumentRef(documentId, caseId, lawyerId, "Договор"));
    when(reviewRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    TabularReviewDto dto =
        service.create(request(List.of(documentId), List.of("Кто стороны?")), lawyerId, List.of());

    verify(reviewRunner).run(dto.id());
  }

  @Test
  void getThrowsWhenReviewDoesNotExist() {
    UUID reviewId = UUID.randomUUID();
    when(reviewRepository.findById(reviewId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.get(reviewId, lawyerId, List.of()))
        .isInstanceOf(TabularReviewNotFoundException.class);
  }

  @Test
  void getChecksVisibilityAndAssemblesDto() {
    TabularReview review = review();
    when(reviewRepository.findById(review.getId())).thenReturn(Optional.of(review));
    TabularReviewDocument document = new TabularReviewDocument();
    document.setDocumentTitle("Договор");
    when(reviewDocumentRepository.findByReviewIdOrderByPositionAsc(review.getId()))
        .thenReturn(List.of(document));
    when(cellRepository.findByReviewId(review.getId())).thenReturn(List.of());

    TabularReviewDto dto = service.get(review.getId(), lawyerId, List.of());

    verify(caseAccessProvider).assertCaseVisible(caseId, lawyerId, List.of());
    assertThat(dto.documents()).hasSize(1);
  }

  @Test
  void findByCaseChecksVisibilityAndMapsSummaries() {
    TabularReview review = review();
    when(reviewRepository.findByCaseIdOrderByCreatedAtDesc(caseId)).thenReturn(List.of(review));

    List<TabularReviewSummaryDto> result = service.findByCase(caseId, lawyerId, List.of());

    verify(caseAccessProvider).assertCaseVisible(caseId, lawyerId, List.of());
    assertThat(result).hasSize(1);
    assertThat(result.get(0).id()).isEqualTo(review.getId());
  }

  @Test
  void deleteRequiresOwnershipAfterVisibilityCheck() {
    TabularReview review = review();
    when(reviewRepository.findById(review.getId())).thenReturn(Optional.of(review));

    service.delete(review.getId(), lawyerId, List.of());

    verify(caseAccessProvider).assertCaseVisible(caseId, lawyerId, List.of());
    verify(caseAccessProvider).assertCaseOwned(caseId, lawyerId);
    verify(reviewRepository).delete(review);
  }

  @Test
  void documentsOfDelegatesToRepository() {
    UUID reviewId = UUID.randomUUID();
    service.documentsOf(reviewId);

    verify(reviewDocumentRepository).findByReviewIdOrderByPositionAsc(reviewId);
  }

  @Test
  void cellsOfDelegatesToRepository() {
    UUID reviewId = UUID.randomUUID();
    service.cellsOf(reviewId);

    verify(cellRepository).findByReviewId(reviewId);
  }

  private TabularReview review() {
    TabularReview review = new TabularReview();
    review.setCaseId(caseId);
    review.setLawyerId(lawyerId);
    review.setTitle("Разбор");
    review.setStatus(TabularReviewStatus.PENDING);
    review.setQuestions(List.of("Кто стороны?"));
    review.setDocumentCount(1);
    review.setQuestionCount(1);
    setId(review, UUID.randomUUID());
    return review;
  }

  private void setId(TabularReview review, UUID id) {
    try {
      java.lang.reflect.Field field = TabularReview.class.getDeclaredField("id");
      field.setAccessible(true);
      field.set(review, id);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }
}
