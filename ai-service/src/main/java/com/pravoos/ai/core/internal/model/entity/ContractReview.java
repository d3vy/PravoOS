package com.pravoos.ai.core.internal.model.entity;

import com.pravoos.ai.core.internal.dto.ContractRisk;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "contract_reviews")
public class ContractReview {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID caseId;

    @Column(nullable = false)
    private UUID documentId;

    @Column(nullable = false)
    private UUID lawyerId;

    @Column(nullable = false, length = 500)
    private String documentTitle;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String summary;

    @Column(nullable = false)
    private short riskScore;

    @Column(nullable = false)
    private int highRiskCount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<ContractRisk> findings;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
        if (findings == null) {
            findings = List.of();
        }
    }

    public UUID getId() { return id; }

    public UUID getCaseId() { return caseId; }
    public void setCaseId(UUID caseId) { this.caseId = caseId; }

    public UUID getDocumentId() { return documentId; }
    public void setDocumentId(UUID documentId) { this.documentId = documentId; }

    public UUID getLawyerId() { return lawyerId; }
    public void setLawyerId(UUID lawyerId) { this.lawyerId = lawyerId; }

    public String getDocumentTitle() { return documentTitle; }
    public void setDocumentTitle(String documentTitle) { this.documentTitle = documentTitle; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public short getRiskScore() { return riskScore; }
    public void setRiskScore(short riskScore) { this.riskScore = riskScore; }

    public int getHighRiskCount() { return highRiskCount; }
    public void setHighRiskCount(int highRiskCount) { this.highRiskCount = highRiskCount; }

    public List<ContractRisk> getFindings() { return findings; }
    public void setFindings(List<ContractRisk> findings) { this.findings = findings; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}
