package com.pravoos.ai.core.internal.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pravoos.ai.core.api.AiTool;
import com.pravoos.ai.core.api.AiToolContext;
import com.pravoos.ai.core.api.AiToolResult;
import com.pravoos.ai.core.api.ToolArguments;
import com.pravoos.ai.core.api.ToolJson;
import com.pravoos.ai.core.api.ToolSchema;
import com.pravoos.ai.core.internal.dto.AiActionProposalResponse;
import com.pravoos.ai.core.internal.service.AiActionProposalService;
import com.pravoos.ai.shared.exception.AiActionProposalNotFoundException;
import com.pravoos.ai.shared.exception.AiActionProposalNotPendingException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

abstract class PendingActionsTool implements AiTool {

  private static final String NOTHING_PENDING = "Нет действий, ожидающих подтверждения.";
  private static final String SAME_TURN_ONLY =
      "Действие только что предложено — решение по нему принимает пользователь, "
          + "а не ты. Опиши подготовленное действие и дождись его ответа.";
  private static final String NOT_PENDING =
      "Указанное действие уже обработано или устарело — подтверждать нечего.";

  private final AiActionProposalService proposalService;
  private final ObjectMapper objectMapper;

  protected PendingActionsTool(AiActionProposalService proposalService, ObjectMapper objectMapper) {
    this.proposalService = proposalService;
    this.objectMapper = objectMapper;
  }

  @Override
  public JsonNode parameters() {
    return ToolSchema.object(objectMapper)
        .optionalUuid(
            "proposalId",
            "Идентификатор конкретного действия. Если не указан, обрабатываются все "
                + "действия диалога, ожидающие решения.")
        .build();
  }

  @Override
  public boolean availableFor(AiToolContext context) {
    return context.isLawyer() && context.conversationId() != null;
  }

  @Override
  public boolean readOnly() {
    return false;
  }

  @Override
  public AiToolResult execute(JsonNode arguments, AiToolContext context) {
    List<AiActionProposalResponse> all = proposalService.pending(context);
    if (all.isEmpty()) {
      return AiToolResult.error(NOTHING_PENDING);
    }
    List<AiActionProposalResponse> pending =
        all.stream().filter(proposal -> decidableNow(proposal, context)).toList();
    if (pending.isEmpty()) {
      return AiToolResult.error(SAME_TURN_ONLY);
    }

    UUID requested = ToolArguments.optionalUuid(arguments, "proposalId");
    List<AiActionProposalResponse> targets =
        requested == null
            ? pending
            : pending.stream().filter(proposal -> proposal.id().equals(requested)).toList();
    if (targets.isEmpty()) {
      return AiToolResult.error(NOT_PENDING);
    }

    List<DecidedAction> decided = new ArrayList<>();
    for (AiActionProposalResponse target : targets) {
      try {
        AiActionProposalResponse outcome = decide(target.id(), context);
        decided.add(
            new DecidedAction(
                outcome.id().toString(),
                outcome.title(),
                outcome.status(),
                outcome.failureReason()));
      } catch (AiActionProposalNotFoundException | AiActionProposalNotPendingException ex) {
        decided.add(new DecidedAction(target.id().toString(), target.title(), "SKIPPED", null));
      }
    }
    return AiToolResult.ok(ToolJson.write(objectMapper, decided));
  }

  protected abstract AiActionProposalResponse decide(UUID proposalId, AiToolContext context);

  private static boolean decidableNow(AiActionProposalResponse proposal, AiToolContext context) {
    return context.turnStartedAt() == null
        || proposal.createdAt().isBefore(context.turnStartedAt());
  }

  protected AiActionProposalService proposalService() {
    return proposalService;
  }

  private record DecidedAction(
      String proposalId, String title, String status, String failureReason) {}
}
