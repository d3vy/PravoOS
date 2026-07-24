package com.pravoos.ai.core.internal.model.entity;

import com.pravoos.ai.core.api.SourceReference;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "ai_responses")
public class AiResponse {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false)
  private UUID caseId;

  @Column(nullable = false)
  private UUID lawyerId;

  @Column(nullable = false, length = 100)
  private String workflowId;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String query;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String result;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private List<SourceReference> sources;

  @Column private Short rating;

  @Column(columnDefinition = "TEXT")
  private String ratingComment;

  @Column(nullable = false)
  private LocalDateTime createdAt;

  @PrePersist
  void prePersist() {
    createdAt = LocalDateTime.now();
    if (sources == null) {
      sources = List.of();
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

  public String getWorkflowId() {
    return workflowId;
  }

  public void setWorkflowId(String workflowId) {
    this.workflowId = workflowId;
  }

  public String getQuery() {
    return query;
  }

  public void setQuery(String query) {
    this.query = query;
  }

  public String getResult() {
    return result;
  }

  public void setResult(String result) {
    this.result = result;
  }

  public List<SourceReference> getSources() {
    return sources;
  }

  public void setSources(List<SourceReference> sources) {
    this.sources = sources;
  }

  public Short getRating() {
    return rating;
  }

  public void setRating(Short rating) {
    this.rating = rating;
  }

  public String getRatingComment() {
    return ratingComment;
  }

  public void setRatingComment(String ratingComment) {
    this.ratingComment = ratingComment;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }
}
