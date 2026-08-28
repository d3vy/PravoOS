package com.pravoos.ai.core.internal.repository.jpa;

import com.pravoos.ai.core.internal.model.entity.AiActionProposal;
import com.pravoos.ai.core.internal.model.entity.AiActionProposalStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiActionProposalRepository extends JpaRepository<AiActionProposal, UUID> {

  @Modifying
  @Query(
      "UPDATE AiActionProposal p SET p.status = :status, p.decidedAt = :decidedAt "
          + "WHERE p.id = :id AND p.userId = :userId "
          + "AND p.status = com.pravoos.ai.core.internal.model.entity.AiActionProposalStatus.PENDING "
          + "AND p.expiresAt > :decidedAt")
  int decide(
      @Param("id") UUID id,
      @Param("userId") UUID userId,
      @Param("status") AiActionProposalStatus status,
      @Param("decidedAt") LocalDateTime decidedAt);

  @Modifying
  @Query(
      "UPDATE AiActionProposal p "
          + "SET p.status = com.pravoos.ai.core.internal.model.entity.AiActionProposalStatus.EXPIRED, "
          + "p.decidedAt = :now "
          + "WHERE p.status = com.pravoos.ai.core.internal.model.entity.AiActionProposalStatus.PENDING "
          + "AND p.expiresAt <= :now")
  int expireOverdue(@Param("now") LocalDateTime now);

  List<AiActionProposal> findByUserIdAndConversationIdAndStatusOrderByCreatedAtAsc(
      UUID userId, String conversationId, AiActionProposalStatus status);

  long countByUserIdAndCreatedAtAfter(UUID userId, LocalDateTime since);
}
