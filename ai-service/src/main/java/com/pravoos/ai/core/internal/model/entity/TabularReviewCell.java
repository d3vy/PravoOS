package com.pravoos.ai.core.internal.model.entity;

import com.pravoos.ai.core.internal.dto.ReviewCitation;
import com.pravoos.ai.shared.model.enums.ReviewAnswerConfidence;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "tabular_review_cells")
public class TabularReviewCell {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false)
  private UUID reviewId;

  @Column(nullable = false)
  private UUID documentId;

  @Column(nullable = false)
  private int questionIndex;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String answer;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private ReviewAnswerConfidence confidence;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private List<ReviewCitation> citations;

  @Column(nullable = false)
  private LocalDateTime createdAt;

  @PrePersist
  void prePersist() {
    createdAt = LocalDateTime.now(ZoneOffset.UTC);
    if (citations == null) {
      citations = List.of();
    }
  }

  public UUID getId() {
    return id;
  }

  public UUID getReviewId() {
    return reviewId;
  }

  public void setReviewId(UUID reviewId) {
    this.reviewId = reviewId;
  }

  public UUID getDocumentId() {
    return documentId;
  }

  public void setDocumentId(UUID documentId) {
    this.documentId = documentId;
  }

  public int getQuestionIndex() {
    return questionIndex;
  }

  public void setQuestionIndex(int questionIndex) {
    this.questionIndex = questionIndex;
  }

  public String getAnswer() {
    return answer;
  }

  public void setAnswer(String answer) {
    this.answer = answer;
  }

  public ReviewAnswerConfidence getConfidence() {
    return confidence;
  }

  public void setConfidence(ReviewAnswerConfidence confidence) {
    this.confidence = confidence;
  }

  public List<ReviewCitation> getCitations() {
    return citations;
  }

  public void setCitations(List<ReviewCitation> citations) {
    this.citations = citations;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }
}
