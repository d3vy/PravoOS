package com.pravoos.user.repository;

import com.pravoos.user.model.entity.UserMfa;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserMfaRepository extends JpaRepository<UserMfa, UUID> {

    Optional<UserMfa> findByUserId(UUID userId);

    boolean existsByUserIdAndEnabledTrue(UUID userId);

    void deleteByUserId(UUID userId);
}
