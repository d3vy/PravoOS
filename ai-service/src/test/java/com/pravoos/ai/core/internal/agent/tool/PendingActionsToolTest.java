package com.pravoos.ai.core.internal.agent.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pravoos.ai.core.api.AiActorRole;
import com.pravoos.ai.core.api.AiToolContext;
import com.pravoos.ai.core.api.AiToolResult;
import com.pravoos.ai.core.internal.dto.AiActionProposalResponse;
import com.pravoos.ai.core.internal.service.AiActionProposalService;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PendingActionsToolTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final UUID LAWYER_ID = UUID.randomUUID();
  private static final LocalDateTime TURN_STARTED_AT = LocalDateTime.now(ZoneOffset.UTC);

  @Mock private AiActionProposalService proposalService;

  private AiToolContext context(LocalDateTime turnStartedAt) {
    return new AiToolContext(LAWYER_ID, List.of(), AiActorRole.LAWYER, "conv-1", turnStartedAt);
  }

  private ApprovePendingActionsTool approveTool() {
    return new ApprovePendingActionsTool(proposalService, MAPPER);
  }

  private static AiActionProposalResponse proposal(UUID id, LocalDateTime createdAt) {
    return new AiActionProposalResponse(
        id,
        "conv-1",
        "msg-1",
        "create_client",
        "Создать клиента «Иванов»",
        "PENDING",
        MAPPER.createObjectNode(),
        null,
        null,
        createdAt,
        createdAt.plusMinutes(30));
  }

  @Test
  void refusesToApproveAProposalCreatedInTheSameTurn() {
    UUID proposalId = UUID.randomUUID();
    when(proposalService.pending(any()))
        .thenReturn(List.of(proposal(proposalId, TURN_STARTED_AT.plusSeconds(1))));

    AiToolResult result =
        approveTool().execute(MAPPER.createObjectNode(), context(TURN_STARTED_AT));

    assertThat(result.ok()).isFalse();
    assertThat(result.content()).contains("решение по нему принимает пользователь");
    verify(proposalService, never()).approve(any(), any());
  }

  @Test
  void approvesAProposalCarriedOverFromAnEarlierTurn() {
    UUID proposalId = UUID.randomUUID();
    LocalDateTime earlier = TURN_STARTED_AT.minusMinutes(1);
    when(proposalService.pending(any())).thenReturn(List.of(proposal(proposalId, earlier)));
    when(proposalService.approve(any(), any())).thenReturn(proposal(proposalId, earlier));

    AiToolResult result =
        approveTool().execute(MAPPER.createObjectNode(), context(TURN_STARTED_AT));

    assertThat(result.ok()).isTrue();
    verify(proposalService).approve(any(), any());
  }

  @Test
  void reportsWhenNothingIsPending() {
    when(proposalService.pending(any())).thenReturn(List.of());

    AiToolResult result =
        approveTool().execute(MAPPER.createObjectNode(), context(TURN_STARTED_AT));

    assertThat(result.ok()).isFalse();
    verify(proposalService, never()).approve(any(), any());
  }

  @Test
  void cancelToolRejectsInsteadOfApproving() {
    UUID proposalId = UUID.randomUUID();
    LocalDateTime earlier = TURN_STARTED_AT.minusMinutes(1);
    when(proposalService.pending(any())).thenReturn(List.of(proposal(proposalId, earlier)));
    when(proposalService.reject(any(), any())).thenReturn(proposal(proposalId, earlier));

    new CancelPendingActionsTool(proposalService, MAPPER)
        .execute(MAPPER.createObjectNode(), context(TURN_STARTED_AT));

    verify(proposalService).reject(any(), any());
    verify(proposalService, never()).approve(any(), any());
  }

  @Test
  void isHiddenOutsideAConversation() {
    assertThat(
            approveTool().availableFor(new AiToolContext(LAWYER_ID, List.of(), AiActorRole.LAWYER)))
        .isFalse();
    assertThat(approveTool().availableFor(context(TURN_STARTED_AT))).isTrue();
  }

  @Test
  void isHiddenFromAnAdminWatchingSomeoneElseConversation() {
    assertThat(
            approveTool()
                .availableFor(
                    new AiToolContext(
                        UUID.randomUUID(),
                        List.of(),
                        AiActorRole.ADMIN,
                        "conv-1",
                        TURN_STARTED_AT)))
        .isFalse();
  }

  @Test
  void isNotDeduplicatedAsAReadOnlyTool() {
    assertThat(approveTool().readOnly()).isFalse();
  }
}
