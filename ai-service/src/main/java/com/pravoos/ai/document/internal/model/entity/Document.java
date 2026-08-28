package com.pravoos.ai.document.internal.model.entity;

import com.pravoos.ai.shared.model.enums.DocumentKind;
import com.pravoos.ai.shared.model.enums.DocumentStatus;
import com.pravoos.ai.shared.model.enums.DocumentSummaryStatus;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "documents")
@SQLRestriction("deleted_at IS NULL")
public class Document {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, length = 500)
  private String title;

  @Column(nullable = false, length = 500)
  private String fileName;

  @Column(nullable = false, length = 50)
  private String fileType;

  @Column(nullable = false, length = 1000)
  private String filePath;

  @Column(nullable = false)
  private UUID uploadedBy;

  @Column(name = "case_id")
  private UUID caseId;

  @Column(name = "size_bytes", nullable = false)
  private long sizeBytes;

  @Column(name = "visible_to_client", nullable = false)
  private boolean visibleToClient = false;

  @Enumerated(EnumType.STRING)
  @Column(name = "document_kind", nullable = false, length = 50)
  private DocumentKind documentKind = DocumentKind.GENERAL;

  @Column(name = "act_canonical", length = 300)
  private String actCanonical;

  @Column(name = "article_number", length = 50)
  private String articleNumber;

  @Column(name = "edition_date")
  private LocalDate editionDate;

  @Column(nullable = false)
  private boolean superseded = false;

  @Column(nullable = false)
  private LocalDateTime uploadedAt;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 50)
  private DocumentStatus status;

  @Column(columnDefinition = "text")
  private String summary;

  @Convert(converter = StringListJsonConverter.class)
  @Column(name = "summary_key_points", columnDefinition = "text")
  private List<String> summaryKeyPoints = new ArrayList<>();

  @Enumerated(EnumType.STRING)
  @Column(name = "summary_status", nullable = false, length = 50)
  private DocumentSummaryStatus summaryStatus = DocumentSummaryStatus.NONE;

  @Column(name = "summary_generated_at")
  private LocalDateTime summaryGeneratedAt;

  @Column(name = "deleted_at")
  private LocalDateTime deletedAt;

  @PrePersist
  void prePersist() {
    uploadedAt = LocalDateTime.now(ZoneOffset.UTC);
    if (status == null) {
      status = DocumentStatus.PROCESSING;
    }
    if (documentKind == null) {
      documentKind = DocumentKind.GENERAL;
    }
    if (summaryStatus == null) {
      summaryStatus = DocumentSummaryStatus.NONE;
    }
  }

  public UUID getId() {
    return id;
  }

  public LocalDateTime getDeletedAt() {
    return deletedAt;
  }

  public void setDeletedAt(LocalDateTime deletedAt) {
    this.deletedAt = deletedAt;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getFileName() {
    return fileName;
  }

  public void setFileName(String fileName) {
    this.fileName = fileName;
  }

  public String getFileType() {
    return fileType;
  }

  public void setFileType(String fileType) {
    this.fileType = fileType;
  }

  public String getFilePath() {
    return filePath;
  }

  public void setFilePath(String filePath) {
    this.filePath = filePath;
  }

  public UUID getUploadedBy() {
    return uploadedBy;
  }

  public void setUploadedBy(UUID uploadedBy) {
    this.uploadedBy = uploadedBy;
  }

  public UUID getCaseId() {
    return caseId;
  }

  public void setCaseId(UUID caseId) {
    this.caseId = caseId;
  }

  public long getSizeBytes() {
    return sizeBytes;
  }

  public void setSizeBytes(long sizeBytes) {
    this.sizeBytes = sizeBytes;
  }

  public boolean isVisibleToClient() {
    return visibleToClient;
  }

  public void setVisibleToClient(boolean visibleToClient) {
    this.visibleToClient = visibleToClient;
  }

  public DocumentKind getDocumentKind() {
    return documentKind;
  }

  public void setDocumentKind(DocumentKind documentKind) {
    this.documentKind = documentKind;
  }

  public String getActCanonical() {
    return actCanonical;
  }

  public void setActCanonical(String actCanonical) {
    this.actCanonical = actCanonical;
  }

  public String getArticleNumber() {
    return articleNumber;
  }

  public void setArticleNumber(String articleNumber) {
    this.articleNumber = articleNumber;
  }

  public LocalDate getEditionDate() {
    return editionDate;
  }

  public void setEditionDate(LocalDate editionDate) {
    this.editionDate = editionDate;
  }

  public boolean isSuperseded() {
    return superseded;
  }

  public void setSuperseded(boolean superseded) {
    this.superseded = superseded;
  }

  public LocalDateTime getUploadedAt() {
    return uploadedAt;
  }

  public DocumentStatus getStatus() {
    return status;
  }

  public void setStatus(DocumentStatus status) {
    this.status = status;
  }

  public String getSummary() {
    return summary;
  }

  public void setSummary(String summary) {
    this.summary = summary;
  }

  public List<String> getSummaryKeyPoints() {
    return summaryKeyPoints;
  }

  public void setSummaryKeyPoints(List<String> summaryKeyPoints) {
    this.summaryKeyPoints.clear();
    if (summaryKeyPoints != null) {
      this.summaryKeyPoints.addAll(summaryKeyPoints);
    }
  }

  public DocumentSummaryStatus getSummaryStatus() {
    return summaryStatus;
  }

  public void setSummaryStatus(DocumentSummaryStatus summaryStatus) {
    this.summaryStatus = summaryStatus;
  }

  public LocalDateTime getSummaryGeneratedAt() {
    return summaryGeneratedAt;
  }

  public void setSummaryGeneratedAt(LocalDateTime summaryGeneratedAt) {
    this.summaryGeneratedAt = summaryGeneratedAt;
  }
}
