package com.pravoos.ai.practice.internal.model.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "case_analyses")
public class CaseAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "case_id", nullable = false)
    private UUID caseId;

    @Column(name = "lawyer_id", nullable = false)
    private UUID lawyerId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "hearing_count", nullable = false)
    private int hearingCount;

    @Column(name = "total_tokens", nullable = false)
    private long totalTokens;

    @Column(name = "generated_at", nullable = false)
    private LocalDateTime generatedAt;

    protected CaseAnalysis() {
    }

    public CaseAnalysis(UUID caseId, UUID lawyerId, String content, int hearingCount, long totalTokens) {
        this.caseId = caseId;
        this.lawyerId = lawyerId;
        this.content = content;
        this.hearingCount = hearingCount;
        this.totalTokens = totalTokens;
    }

    @PrePersist
    @PreUpdate
    void touch() {
        generatedAt = LocalDateTime.now();
    }

    public void update(UUID lawyerId, String content, int hearingCount, long totalTokens) {
        this.lawyerId = lawyerId;
        this.content = content;
        this.hearingCount = hearingCount;
        this.totalTokens = totalTokens;
    }

    public UUID getId() { return id; }
    public UUID getCaseId() { return caseId; }
    public UUID getLawyerId() { return lawyerId; }
    public String getContent() { return content; }
    public int getHearingCount() { return hearingCount; }
    public long getTotalTokens() { return totalTokens; }
    public LocalDateTime getGeneratedAt() { return generatedAt; }
}
