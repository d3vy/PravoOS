package com.pravoos.user.identity.internal.repository;

import com.pravoos.user.identity.internal.model.entity.UserMfa;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserMfaRepository extends JpaRepository<UserMfa, UUID> {

  Optional<UserMfa> findByUserId(UUID userId);

  boolean existsByUserIdAndEnabledTrue(UUID userId);

  void deleteByUserId(UUID userId);
}
