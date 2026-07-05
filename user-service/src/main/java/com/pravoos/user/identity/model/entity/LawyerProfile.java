package com.pravoos.user.identity.model.entity;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "lawyer_profiles")
public class LawyerProfile {

    @Id
    private UUID userId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false)
    private String fullName;

    @Column(length = 255)
    private String specialization;

    @Column(length = 50)
    private String phone;

    @Column(name = "telegram_chat_id")
    private Long telegramChatId;

    public UUID getUserId() { return userId; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getSpecialization() { return specialization; }
    public void setSpecialization(String specialization) { this.specialization = specialization; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public Long getTelegramChatId() { return telegramChatId; }
    public void setTelegramChatId(Long telegramChatId) { this.telegramChatId = telegramChatId; }
}
