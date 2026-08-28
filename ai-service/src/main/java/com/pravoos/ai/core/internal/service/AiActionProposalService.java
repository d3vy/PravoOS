package com.pravoos.ai.core.internal.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.pravoos.ai.core.api.AiActionProposals;
import com.pravoos.ai.core.api.AiToolContext;
import com.pravoos.ai.core.api.AiToolResult;
import com.pravoos.ai.core.api.AiWriteTool;
import com.pravoos.ai.core.api.ProposedAction;
import com.pravoos.ai.core.internal.agent.AgentMetrics;
import com.pravoos.ai.core.internal.agent.AgentProperties;
import com.pravoos.ai.core.internal.agent.AiToolRegistry;
import com.pravoos.ai.core.internal.dto.AiActionProposalResponse;
import com.pravoos.ai.core.internal.dto.AiTrustedToolResponse;
import com.pravoos.ai.core.internal.model.entity.AiActionProposal;
import com.pravoos.ai.core.internal.model.entity.AiActionProposalStatus;
import com.pravoos.ai.core.internal.model.entity.AiTrustedTool;
import com.pravoos.ai.core.internal.repository.jpa.AiTrustedToolRepository;
import com.pravoos.ai.shared.exception.AiWriteActionRateLimitException;
import com.pravoos.ai.shared.model.enums.AuditAction;
import com.pravoos.ai.shared.service.AccessAuditService;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

@Service
public class AiActionProposalService implements AiActionProposals {

  private static final Logger log = LoggerFactory.getLogger(AiActionProposalService.class);
  private static final Duration RATE_LIMIT_WINDOW = Duration.ofHours(1);
  private static final String TOOL_UNAVAILABLE = "Инструмент больше недоступен.";
  private static final String CONVERSATION_REQUIRED =
      "Подтверждаемое действие должно принадлежать диалогу";

  private final AiActionProposalStore proposalStore;
  private final AiToolRegistry toolRegistry;
  private final AgentProperties agentProperties;
  private final AccessAuditService accessAuditService;
  private final AiTrustedToolRepository trustedToolRepository;
  private final AgentMetrics agentMetrics;

  public AiActionProposalService(
      AiActionProposalStore proposalStore,
      @Lazy AiToolRegistry toolRegistry,
      AgentProperties agentProperties,
      AccessAuditService accessAuditService,
      AiTrustedToolRepository trustedToolRepository,
      AgentMetrics agentMetrics) {
    this.proposalStore = proposalStore;
    this.toolRegistry = toolRegistry;
    this.agentProperties = agentProperties;
    this.accessAuditService = accessAuditService;
    this.trustedToolRepository = trustedToolRepository;
    this.agentMetrics = agentMetrics;
  }

  @Override
  public ProposedAction propose(
      String toolName, JsonNode arguments, String title, AiToolContext context) {
    if (context.conversationId() == null) {
      throw new IllegalStateException(CONVERSATION_REQUIRED);
    }
    LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
    assertWithinWriteRateLimit(context.userId(), now);

    AiActionProposal saved =
        proposalStore.create(
            context.singleOrgId(),
            context.userId(),
            context.conversationId(),
            toolName,
            arguments,
            title,
            now,
            now.plus(agentProperties.proposalTtl()));

    accessAuditService.recordAgentAction(
        context.userId(),
        context.role().name(),
        AuditAction.AI_ACTION_PROPOSE,
        saved.getId(),
        toolName);
    agentMetrics.recordProposal(toolName, "created");
    log.info(
        "AI action proposed: tool={}, proposal={}, user={}",
        toolName,
        saved.getId(),
        context.userId());
    return new ProposedAction(saved.getId(), toolName, saved.getTitle(), saved.getExpiresAt());
  }

  public List<AiActionProposalResponse> pending(AiToolContext context) {
    return proposalStore.pending(context.userId(), context.conversationId()).stream()
        .map(AiActionProposalResponse::from)
        .toList();
  }

  public List<AiActionProposalResponse> bindToMessage(
      UUID userId, String conversationId, LocalDateTime since, String messageId) {
    return proposalStore.bindToMessage(userId, conversationId, since, messageId).stream()
        .map(AiActionProposalResponse::from)
        .toList();
  }

  public AiActionProposalResponse approve(UUID proposalId, AiToolContext context) {
    return approve(proposalId, context, false);
  }

  public AiActionProposalResponse approve(
      UUID proposalId, AiToolContext context, boolean alwaysAllow) {
    LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
    AiActionProposal proposal =
        proposalStore.claim(proposalId, context.userId(), AiActionProposalStatus.APPROVED, now);

    if (alwaysAllow) {
      trust(proposal.getToolName(), context);
    }

    AiToolContext executionContext =
        new AiToolContext(
            context.userId(), context.orgIds(), context.role(), proposal.getConversationId());
    AiToolResult outcome = perform(proposal, executionContext);

    if (outcome.ok()) {
      proposalStore.recordSuccess(proposalId, outcome.content());
      proposal.setResult(outcome.content());
      agentMetrics.recordProposal(proposal.getToolName(), "approved");
    } else {
      proposalStore.recordFailure(proposalId, outcome.content());
      proposal.setStatus(AiActionProposalStatus.FAILED);
      proposal.setFailureReason(outcome.content());
      agentMetrics.recordProposal(proposal.getToolName(), "failed");
    }

    accessAuditService.recordAgentAction(
        context.userId(),
        context.role().name(),
        AuditAction.AI_ACTION_APPROVE,
        proposalId,
        proposal.getToolName());
    return AiActionProposalResponse.from(proposal);
  }

  public AiActionProposalResponse reject(UUID proposalId, AiToolContext context) {
    LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
    AiActionProposal proposal =
        proposalStore.claim(proposalId, context.userId(), AiActionProposalStatus.REJECTED, now);
    accessAuditService.recordAgentAction(
        context.userId(),
        context.role().name(),
        AuditAction.AI_ACTION_REJECT,
        proposalId,
        proposal.getToolName());
    agentMetrics.recordProposal(proposal.getToolName(), "rejected");
    return AiActionProposalResponse.from(proposal);
  }

  @Override
  public boolean isTrusted(String toolName, AiToolContext context) {
    return trustedToolRepository.existsByUserIdAndToolName(context.userId(), toolName);
  }

  public void trust(String toolName, AiToolContext context) {
    if (isTrusted(toolName, context)) {
      return;
    }
    trustedToolRepository.save(
        new AiTrustedTool(
            context.singleOrgId(), context.userId(), toolName, LocalDateTime.now(ZoneOffset.UTC)));
    accessAuditService.recordAgentAction(
        context.userId(), context.role().name(), AuditAction.AI_TOOL_TRUST_GRANT, null, toolName);
    log.info("AI tool '{}' trusted by user {}", toolName, context.userId());
  }

  public void revoke(String toolName, AiToolContext context) {
    trustedToolRepository.deleteByUserIdAndToolName(context.userId(), toolName);
    accessAuditService.recordAgentAction(
        context.userId(), context.role().name(), AuditAction.AI_TOOL_TRUST_REVOKE, null, toolName);
    log.info("AI tool '{}' trust revoked by user {}", toolName, context.userId());
  }

  public List<AiTrustedToolResponse> trusted(AiToolContext context) {
    return trustedToolRepository.findByUserIdOrderByGrantedAtDesc(context.userId()).stream()
        .map(AiTrustedToolResponse::from)
        .toList();
  }

  public int expireOverdue() {
    int expired = proposalStore.expireOverdue(LocalDateTime.now(ZoneOffset.UTC));
    if (expired > 0) {
      log.info("Expired {} pending AI action proposal(s)", expired);
      agentMetrics.recordProposal("ALL", "expired", expired);
    }
    return expired;
  }

  private AiToolResult perform(AiActionProposal proposal, AiToolContext context) {
    Optional<AiWriteTool> tool = writeTool(proposal.getToolName(), context);
    if (tool.isEmpty()) {
      log.warn(
          "Approved proposal {} references unavailable write tool '{}'",
          proposal.getId(),
          proposal.getToolName());
      return AiToolResult.error(TOOL_UNAVAILABLE);
    }
    try {
      AiToolResult result = tool.get().perform(proposal.getArguments(), context);
      return result == null ? AiToolResult.error(TOOL_UNAVAILABLE) : result;
    } catch (RuntimeException ex) {
      log.warn(
          "Approved proposal {} failed in tool '{}'", proposal.getId(), proposal.getToolName(), ex);
      return AiToolResult.error(ex.getMessage() == null ? TOOL_UNAVAILABLE : ex.getMessage());
    }
  }

  private Optional<AiWriteTool> writeTool(String toolName, AiToolContext context) {
    return toolRegistry
        .find(toolName, context)
        .filter(AiWriteTool.class::isInstance)
        .map(AiWriteTool.class::cast);
  }

  private void assertWithinWriteRateLimit(UUID userId, LocalDateTime now) {
    long recent = proposalStore.countCreatedSince(userId, now.minus(RATE_LIMIT_WINDOW));
    if (recent >= agentProperties.maxWriteActionsPerHour()) {
      log.warn(
          "AI write action rate limit reached for user {} ({} in the last hour)", userId, recent);
      throw new AiWriteActionRateLimitException();
    }
  }
}
