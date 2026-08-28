package com.pravoos.ai.core.internal.agent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pravoos.ai.core.api.AiToolContext;
import com.pravoos.ai.core.internal.dto.AiActionProposalResponse;
import com.pravoos.ai.core.internal.service.AiActionProposalService;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class CancelPendingActionsTool extends PendingActionsTool {

  public CancelPendingActionsTool(
      AiActionProposalService proposalService, ObjectMapper objectMapper) {
    super(proposalService, objectMapper);
  }

  @Override
  public String name() {
    return "cancel_pending_actions";
  }

  @Override
  public String description() {
    return "Отменяет действия, ожидающие подтверждения в этом диалоге. Вызывай, когда "
        + "пользователь отказался — «нет», «отмена», «не надо».";
  }

  @Override
  protected AiActionProposalResponse decide(UUID proposalId, AiToolContext context) {
    return proposalService().reject(proposalId, context);
  }
}
