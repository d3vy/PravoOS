package com.pravoos.user.repository;

import com.pravoos.user.model.entity.LawyerApplication;
import com.pravoos.user.model.enums.ApplicationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LawyerApplicationRepository extends JpaRepository<LawyerApplication, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM LawyerApplication a WHERE a.id = :id")
    Optional<LawyerApplication> findByIdForUpdate(@Param("id") UUID id);

    List<LawyerApplication> findByStatusOrderBySubmittedAtDesc(ApplicationStatus status);

    List<LawyerApplication> findAllByOrderBySubmittedAtDesc();

    boolean existsByEmailAndStatus(String email, ApplicationStatus status);

    Optional<LawyerApplication> findByEmailVerificationToken(String token);

    Optional<LawyerApplication> findByEmailAndStatusAndEmailVerifiedFalse(String email, ApplicationStatus status);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE LawyerApplication a SET a.emailVerificationToken = null, a.emailVerificationExpiresAt = null " +
            "WHERE a.emailVerificationExpiresAt < :now AND a.emailVerified = false")
    int clearExpiredVerificationTokens(@Param("now") LocalDateTime now);
}
