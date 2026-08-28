package com.pravoos.ai.core.internal.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.pravoos.ai.core.internal.model.entity.AiActionProposal;
import com.pravoos.ai.core.internal.model.entity.AiActionProposalStatus;
import com.pravoos.ai.core.internal.repository.jpa.AiActionProposalRepository;
import com.pravoos.ai.shared.exception.AiActionProposalNotFoundException;
import com.pravoos.ai.shared.exception.AiActionProposalNotPendingException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiActionProposalStore {

  private static final int RESULT_MAX_CHARS = 4000;
  private static final int FAILURE_REASON_MAX_CHARS = 1000;

  private final AiActionProposalRepository proposalRepository;

  public AiActionProposalStore(AiActionProposalRepository proposalRepository) {
    this.proposalRepository = proposalRepository;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public AiActionProposal create(
      UUID orgId,
      UUID userId,
      String conversationId,
      String toolName,
      JsonNode arguments,
      String title,
      LocalDateTime createdAt,
      LocalDateTime expiresAt) {
    return proposalRepository.save(
        new AiActionProposal(
            orgId, userId, conversationId, toolName, arguments, title, createdAt, expiresAt));
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public AiActionProposal claim(
      UUID proposalId, UUID userId, AiActionProposalStatus target, LocalDateTime decidedAt) {
    AiActionProposal proposal =
        proposalRepository
            .findById(proposalId)
            .filter(candidate -> candidate.getUserId().equals(userId))
            .orElseThrow(() -> new AiActionProposalNotFoundException(proposalId));
    if (proposalRepository.decide(proposalId, userId, target, decidedAt) == 0) {
      throw new AiActionProposalNotPendingException(proposalId);
    }
    proposal.setStatus(target);
    proposal.setDecidedAt(decidedAt);
    return proposal;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void recordSuccess(UUID proposalId, String result) {
    proposalRepository
        .findById(proposalId)
        .ifPresent(proposal -> proposal.setResult(truncate(result, RESULT_MAX_CHARS)));
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void recordFailure(UUID proposalId, String failureReason) {
    proposalRepository
        .findById(proposalId)
        .ifPresent(
            proposal -> {
              proposal.setStatus(AiActionProposalStatus.FAILED);
              proposal.setFailureReason(truncate(failureReason, FAILURE_REASON_MAX_CHARS));
            });
  }

  @Transactional(readOnly = true)
  public List<AiActionProposal> pending(UUID userId, String conversationId) {
    return proposalRepository.findByUserIdAndConversationIdAndStatusOrderByCreatedAtAsc(
        userId, conversationId, AiActionProposalStatus.PENDING);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public List<AiActionProposal> bindToMessage(
      UUID userId, String conversationId, LocalDateTime since, String messageId) {
    List<AiActionProposal> proposals =
        proposalRepository
            .findByUserIdAndConversationIdAndStatusOrderByCreatedAtAsc(
                userId, conversationId, AiActionProposalStatus.PENDING)
            .stream()
            .filter(proposal -> !proposal.getCreatedAt().isBefore(since))
            .toList();
    proposals.forEach(proposal -> proposal.setMessageId(messageId));
    return proposals;
  }

  @Transactional(readOnly = true)
  public long countCreatedSince(UUID userId, LocalDateTime since) {
    return proposalRepository.countByUserIdAndCreatedAtAfter(userId, since);
  }

  @Transactional
  public int expireOverdue(LocalDateTime now) {
    return proposalRepository.expireOverdue(now);
  }

  private static String truncate(String value, int maxChars) {
    if (value == null) {
      return null;
    }
    return value.length() <= maxChars ? value : value.substring(0, maxChars) + "…";
  }
}
