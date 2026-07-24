package com.pravoos.ai.core.internal.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.pravoos.ai.core.internal.model.entity.TabularReview;
import com.pravoos.ai.core.internal.model.entity.TabularReviewCell;
import com.pravoos.ai.shared.model.enums.ReviewAnswerConfidence;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TabularReviewDtoTest {

  @Test
  void filledCellsCountsOnlyAnsweredCellsIgnoringNotFound() {
    TabularReview review = review(2, 3);
    List<TabularReviewCell> cells =
        List.of(
            cell(ReviewAnswerConfidence.HIGH),
            cell(ReviewAnswerConfidence.MEDIUM),
            cell(ReviewAnswerConfidence.NOT_FOUND),
            cell(ReviewAnswerConfidence.LOW));

    TabularReviewDto dto = TabularReviewDto.from(review, List.of(), cells);

    assertThat(dto.filledCells()).isEqualTo(3);
    assertThat(dto.totalCells()).isEqualTo(6);
  }

  @Test
  void filledCellsIsZeroWhenAllDocumentsFailedAndNoCellsExist() {
    TabularReview review = review(2, 3);

    TabularReviewDto dto = TabularReviewDto.from(review, List.of(), List.of());

    assertThat(dto.filledCells()).isZero();
    assertThat(dto.totalCells()).isEqualTo(6);
  }

  private TabularReview review(int documentCount, int questionCount) {
    TabularReview review = new TabularReview();
    review.setCaseId(UUID.randomUUID());
    review.setTitle("Разбор");
    review.setQuestions(List.of("q1", "q2", "q3"));
    review.setDocumentCount(documentCount);
    review.setQuestionCount(questionCount);
    return review;
  }

  private TabularReviewCell cell(ReviewAnswerConfidence confidence) {
    TabularReviewCell cell = new TabularReviewCell();
    cell.setReviewId(UUID.randomUUID());
    cell.setDocumentId(UUID.randomUUID());
    cell.setQuestionIndex(0);
    cell.setAnswer("answer");
    cell.setConfidence(confidence);
    cell.setCitations(List.of());
    return cell;
  }
}
