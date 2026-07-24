package com.pravoos.user.collaboration.internal.repository;

import com.pravoos.user.collaboration.internal.model.entity.TelegramLinkCode;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TelegramLinkCodeRepository extends JpaRepository<TelegramLinkCode, String> {

  void deleteByUserId(UUID userId);

  int deleteByExpiresAtBefore(LocalDateTime cutoff);
}
