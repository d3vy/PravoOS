package com.pravoos.user.model.entity;

import com.pravoos.user.model.enums.ApplicationStatus;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "lawyer_applications")
public class LawyerApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String fullName;

    @Column(nullable = false)
    private String passwordHash;

    @Column(length = 100)
    private String barNumber;

    @Column(length = 255)
    private String specialization;

    @Column(length = 50)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ApplicationStatus status;

    @Column(nullable = false)
    private LocalDateTime submittedAt;

    private LocalDateTime reviewedAt;

    private UUID reviewedBy;

    @Column(unique = true)
    private String emailVerificationToken;

    private LocalDateTime emailVerificationExpiresAt;

    @Column(nullable = false)
    private boolean emailVerified = false;

    @PrePersist
    void prePersist() {
        submittedAt = LocalDateTime.now();
        if (status == null) {
            status = ApplicationStatus.PENDING;
        }
    }

    public UUID getId() { return id; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getBarNumber() { return barNumber; }
    public void setBarNumber(String barNumber) { this.barNumber = barNumber; }

    public String getSpecialization() { return specialization; }
    public void setSpecialization(String specialization) { this.specialization = specialization; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public ApplicationStatus getStatus() { return status; }
    public void setStatus(ApplicationStatus status) { this.status = status; }

    public LocalDateTime getSubmittedAt() { return submittedAt; }

    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }

    public UUID getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(UUID reviewedBy) { this.reviewedBy = reviewedBy; }

    public String getEmailVerificationToken() { return emailVerificationToken; }
    public void setEmailVerificationToken(String emailVerificationToken) { this.emailVerificationToken = emailVerificationToken; }

    public LocalDateTime getEmailVerificationExpiresAt() { return emailVerificationExpiresAt; }
    public void setEmailVerificationExpiresAt(LocalDateTime emailVerificationExpiresAt) { this.emailVerificationExpiresAt = emailVerificationExpiresAt; }

    public boolean isEmailVerified() { return emailVerified; }
    public void setEmailVerified(boolean emailVerified) { this.emailVerified = emailVerified; }
}
