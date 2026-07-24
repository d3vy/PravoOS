package com.pravoos.ai.core.internal.model.entity;

import com.pravoos.ai.shared.model.enums.TabularReviewStatus;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "tabular_reviews")
public class TabularReview {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false)
  private UUID caseId;

  @Column(nullable = false)
  private UUID lawyerId;

  @Column(nullable = false, length = 300)
  private String title;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private TabularReviewStatus status;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private List<String> questions;

  @Column(nullable = false)
  private int documentCount;

  @Column(nullable = false)
  private int questionCount;

  @Column(length = 500)
  private String errorMessage;

  @Column(nullable = false)
  private LocalDateTime createdAt;

  private LocalDateTime completedAt;

  @PrePersist
  void prePersist() {
    createdAt = LocalDateTime.now(ZoneOffset.UTC);
    if (questions == null) {
      questions = List.of();
    }
    if (status == null) {
      status = TabularReviewStatus.PENDING;
    }
  }

  public UUID getId() {
    return id;
  }

  public UUID getCaseId() {
    return caseId;
  }

  public void setCaseId(UUID caseId) {
    this.caseId = caseId;
  }

  public UUID getLawyerId() {
    return lawyerId;
  }

  public void setLawyerId(UUID lawyerId) {
    this.lawyerId = lawyerId;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public TabularReviewStatus getStatus() {
    return status;
  }

  public void setStatus(TabularReviewStatus status) {
    this.status = status;
  }

  public List<String> getQuestions() {
    return questions;
  }

  public void setQuestions(List<String> questions) {
    this.questions = questions;
  }

  public int getDocumentCount() {
    return documentCount;
  }

  public void setDocumentCount(int documentCount) {
    this.documentCount = documentCount;
  }

  public int getQuestionCount() {
    return questionCount;
  }

  public void setQuestionCount(int questionCount) {
    this.questionCount = questionCount;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public void setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public LocalDateTime getCompletedAt() {
    return completedAt;
  }

  public void setCompletedAt(LocalDateTime completedAt) {
    this.completedAt = completedAt;
  }
}
