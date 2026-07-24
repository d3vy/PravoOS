package com.pravoos.ai.core.internal.model.entity;

import com.pravoos.ai.shared.model.enums.TabularReviewStatus;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "tabular_review_documents")
public class TabularReviewDocument {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false)
  private UUID reviewId;

  @Column(nullable = false)
  private UUID documentId;

  @Column(nullable = false, length = 500)
  private String documentTitle;

  @Column(nullable = false)
  private int position;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private TabularReviewStatus status;

  @Column(length = 500)
  private String errorMessage;

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

  public String getDocumentTitle() {
    return documentTitle;
  }

  public void setDocumentTitle(String documentTitle) {
    this.documentTitle = documentTitle;
  }

  public int getPosition() {
    return position;
  }

  public void setPosition(int position) {
    this.position = position;
  }

  public TabularReviewStatus getStatus() {
    return status;
  }

  public void setStatus(TabularReviewStatus status) {
    this.status = status;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public void setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
  }
}
