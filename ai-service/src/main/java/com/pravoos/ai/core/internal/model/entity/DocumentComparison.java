package com.pravoos.ai.core.internal.model.entity;

import com.pravoos.ai.core.internal.dto.DiffChange;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "document_comparisons")
public class DocumentComparison {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false)
  private UUID caseId;

  @Column(nullable = false)
  private UUID baseDocumentId;

  @Column(nullable = false)
  private UUID revisedDocumentId;

  @Column(nullable = false)
  private UUID lawyerId;

  @Column(nullable = false, length = 500)
  private String baseDocumentTitle;

  @Column(nullable = false, length = 500)
  private String revisedDocumentTitle;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String summary;

  @Column(nullable = false)
  private short riskScore;

  @Column(nullable = false)
  private int changeCount;

  @Column(nullable = false)
  private int highRiskCount;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private List<DiffChange> changes;

  @Column(nullable = false)
  private LocalDateTime createdAt;

  @PrePersist
  void prePersist() {
    createdAt = LocalDateTime.now(ZoneOffset.UTC);
    if (changes == null) {
      changes = List.of();
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

  public UUID getBaseDocumentId() {
    return baseDocumentId;
  }

  public void setBaseDocumentId(UUID baseDocumentId) {
    this.baseDocumentId = baseDocumentId;
  }

  public UUID getRevisedDocumentId() {
    return revisedDocumentId;
  }

  public void setRevisedDocumentId(UUID revisedDocumentId) {
    this.revisedDocumentId = revisedDocumentId;
  }

  public UUID getLawyerId() {
    return lawyerId;
  }

  public void setLawyerId(UUID lawyerId) {
    this.lawyerId = lawyerId;
  }

  public String getBaseDocumentTitle() {
    return baseDocumentTitle;
  }

  public void setBaseDocumentTitle(String baseDocumentTitle) {
    this.baseDocumentTitle = baseDocumentTitle;
  }

  public String getRevisedDocumentTitle() {
    return revisedDocumentTitle;
  }

  public void setRevisedDocumentTitle(String revisedDocumentTitle) {
    this.revisedDocumentTitle = revisedDocumentTitle;
  }

  public String getSummary() {
    return summary;
  }

  public void setSummary(String summary) {
    this.summary = summary;
  }

  public short getRiskScore() {
    return riskScore;
  }

  public void setRiskScore(short riskScore) {
    this.riskScore = riskScore;
  }

  public int getChangeCount() {
    return changeCount;
  }

  public void setChangeCount(int changeCount) {
    this.changeCount = changeCount;
  }

  public int getHighRiskCount() {
    return highRiskCount;
  }

  public void setHighRiskCount(int highRiskCount) {
    this.highRiskCount = highRiskCount;
  }

  public List<DiffChange> getChanges() {
    return changes;
  }

  public void setChanges(List<DiffChange> changes) {
    this.changes = changes;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }
}
