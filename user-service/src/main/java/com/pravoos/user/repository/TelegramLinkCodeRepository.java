package com.pravoos.user.repository;

import com.pravoos.user.model.entity.TelegramLinkCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.UUID;

public interface TelegramLinkCodeRepository extends JpaRepository<TelegramLinkCode, String> {

    void deleteByUserId(UUID userId);

    int deleteByExpiresAtBefore(LocalDateTime cutoff);
}
