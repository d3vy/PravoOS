package com.pravoos.user.identity.internal.repository;

import com.pravoos.user.identity.internal.model.entity.UserMfa;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserMfaRepository extends JpaRepository<UserMfa, UUID> {

    Optional<UserMfa> findByUserId(UUID userId);

    boolean existsByUserIdAndEnabledTrue(UUID userId);

    void deleteByUserId(UUID userId);
}
