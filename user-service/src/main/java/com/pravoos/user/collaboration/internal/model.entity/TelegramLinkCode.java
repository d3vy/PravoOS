package com.pravoos.user.collaboration.internal.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "telegram_link_codes")
public class TelegramLinkCode {

    @Id
    private String code;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected TelegramLinkCode() {
    }

    public TelegramLinkCode(String code, UUID userId, LocalDateTime expiresAt, LocalDateTime createdAt) {
        this.code = code;
        this.userId = userId;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
    }

    public String getCode() { return code; }

    public UUID getUserId() { return userId; }

    public LocalDateTime getExpiresAt() { return expiresAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}
