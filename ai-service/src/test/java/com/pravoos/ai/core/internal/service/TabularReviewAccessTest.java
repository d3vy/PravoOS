package com.pravoos.ai.core.internal.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pravoos.ai.core.api.CaseAccessProvider;
import com.pravoos.ai.core.internal.dto.CreateTabularReviewRequest;
import com.pravoos.ai.core.internal.model.entity.TabularReview;
import com.pravoos.ai.core.internal.repository.jpa.TabularReviewCellRepository;
import com.pravoos.ai.core.internal.repository.jpa.TabularReviewDocumentRepository;
import com.pravoos.ai.core.internal.repository.jpa.TabularReviewRepository;
import com.pravoos.ai.document.api.DocumentAccess;
import com.pravoos.ai.document.api.DocumentRef;
import com.pravoos.ai.shared.config.TabularReviewProperties;
import com.pravoos.ai.shared.exception.CaseNotFoundException;
import com.pravoos.ai.shared.exception.LlmQuotaExceededException;
import com.pravoos.ai.shared.service.LlmQuotaService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TabularReviewAccessTest {

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
            new TabularReviewProperties(20, 10, 4, 40000, 2000, 500));
  }

  @Test
  void createRejectsColleagueWhoDoesNotOwnTheCase() {
    doThrow(new CaseNotFoundException(caseId))
        .when(caseAccessProvider)
        .assertCaseOwned(caseId, lawyerId);

    assertThatThrownBy(() -> service.create(request(), lawyerId, List.of(UUID.randomUUID())))
        .isInstanceOf(CaseNotFoundException.class);

    verify(reviewRepository, never()).save(any());
  }

  @Test
  void createRejectsReviewThatDoesNotFitRemainingDailyQuota() {
    when(documentAccess.findForReview(documentId))
        .thenReturn(new DocumentRef(documentId, caseId, lawyerId, "Договор"));
    doThrow(new LlmQuotaExceededException()).when(llmQuotaService).assertQuotaHeadroom(lawyerId, 1);

    assertThatThrownBy(() -> service.create(request(), lawyerId, List.of()))
        .isInstanceOf(LlmQuotaExceededException.class);

    verify(reviewRepository, never()).save(any());
  }

  @Test
  void deleteRejectsColleagueWhoDoesNotOwnTheCase() {
    UUID reviewId = UUID.randomUUID();
    TabularReview review = new TabularReview();
    review.setCaseId(caseId);
    when(reviewRepository.findById(reviewId)).thenReturn(java.util.Optional.of(review));
    doThrow(new CaseNotFoundException(caseId))
        .when(caseAccessProvider)
        .assertCaseOwned(caseId, lawyerId);

    assertThatThrownBy(() -> service.delete(reviewId, lawyerId, List.of()))
        .isInstanceOf(CaseNotFoundException.class);

    verify(reviewRepository, never()).delete(any());
  }

  private CreateTabularReviewRequest request() {
    return new CreateTabularReviewRequest(
        caseId, "Разбор", List.of(documentId), List.of("Кто стороны?"));
  }
}
