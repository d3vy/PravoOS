package com.pravoos.ai.practice.internal.model.entity;

import com.pravoos.ai.shared.model.enums.MessageAuthorRole;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "case_messages")
public class CaseMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "case_id", nullable = false)
    private UUID caseId;

    @Column(name = "author_user_id", nullable = false)
    private UUID authorUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "author_role", nullable = false, length = 20)
    private MessageAuthorRole authorRole;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected CaseMessage() {
    }

    public CaseMessage(UUID caseId, UUID authorUserId, MessageAuthorRole authorRole, String body) {
        this.caseId = caseId;
        this.authorUserId = authorUserId;
        this.authorRole = authorRole;
        this.body = body;
    }

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    public UUID getId() { return id; }

    public UUID getCaseId() { return caseId; }

    public UUID getAuthorUserId() { return authorUserId; }

    public MessageAuthorRole getAuthorRole() { return authorRole; }

    public String getBody() { return body; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}
