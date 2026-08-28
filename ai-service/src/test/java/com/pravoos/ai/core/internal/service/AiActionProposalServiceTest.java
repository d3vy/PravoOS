package com.pravoos.ai.core.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pravoos.ai.core.api.AiActorRole;
import com.pravoos.ai.core.api.AiToolContext;
import com.pravoos.ai.core.api.AiToolResult;
import com.pravoos.ai.core.api.AiWriteTool;
import com.pravoos.ai.core.api.ProposedAction;
import com.pravoos.ai.core.internal.agent.AgentProperties;
import com.pravoos.ai.core.internal.agent.AiToolRegistry;
import com.pravoos.ai.core.internal.dto.AiActionProposalResponse;
import com.pravoos.ai.core.internal.model.entity.AiActionProposal;
import com.pravoos.ai.core.internal.model.entity.AiActionProposalStatus;
import com.pravoos.ai.core.internal.repository.jpa.AiTrustedToolRepository;
import com.pravoos.ai.shared.exception.AiActionProposalNotPendingException;
import com.pravoos.ai.shared.exception.AiWriteActionRateLimitException;
import com.pravoos.ai.shared.model.enums.AuditAction;
import com.pravoos.ai.shared.service.AccessAuditService;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AiActionProposalServiceTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final AgentProperties PROPERTIES =
      new AgentProperties(
          8, 12, 8000, 8000, Duration.ofSeconds(120), 120_000L, Duration.ofMinutes(30), 3);

  private static final UUID LAWYER_ID = UUID.randomUUID();
  private static final UUID ORG_ID = UUID.randomUUID();
  private static final String CONVERSATION_ID = "conv-1";
  private static final AiToolContext CONTEXT =
      new AiToolContext(LAWYER_ID, List.of(ORG_ID), AiActorRole.LAWYER, CONVERSATION_ID);

  @Mock private AiActionProposalStore proposalStore;
  @Mock private AccessAuditService accessAuditService;
  @Mock private AiTrustedToolRepository trustedToolRepository;

  private RecordingWriteTool writeTool;
  private AiActionProposalService service;

  @BeforeEach
  void setUp() {
    writeTool = new RecordingWriteTool();
    service =
        new AiActionProposalService(
            proposalStore,
            new AiToolRegistry(List.of(writeTool)),
            PROPERTIES,
            accessAuditService,
            trustedToolRepository);
  }

  @Test
  void proposeStoresPendingActionWithOrgFromContextNotFromModelArguments() {
    JsonNode arguments = MAPPER.createObjectNode().put("orgId", UUID.randomUUID().toString());
    AiActionProposal stored = proposal(AiActionProposalStatus.PENDING);
    when(proposalStore.create(any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(stored);

    ProposedAction proposed = service.propose("create_client", arguments, "Создать", CONTEXT);

    assertThat(proposed.id()).isEqualTo(stored.getId());
    verify(proposalStore)
        .create(
            eq(ORG_ID),
            eq(LAWYER_ID),
            eq(CONVERSATION_ID),
            eq("create_client"),
            eq(arguments),
            eq("Создать"),
            any(),
            any());
    verify(accessAuditService)
        .recordAgentAction(
            eq(LAWYER_ID),
            eq("LAWYER"),
            eq(AuditAction.AI_ACTION_PROPOSE),
            any(),
            eq("create_client"));
  }

  @Test
  void proposeRefusesOnceTheHourlyWriteLimitIsReached() {
    when(proposalStore.countCreatedSince(eq(LAWYER_ID), any())).thenReturn(3L);

    assertThatThrownBy(
            () -> service.propose("create_client", MAPPER.createObjectNode(), "Создать", CONTEXT))
        .isInstanceOf(AiWriteActionRateLimitException.class);

    verify(proposalStore, never()).create(any(), any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  void proposeRequiresAConversation() {
    AiToolContext outsideConversation =
        new AiToolContext(LAWYER_ID, List.of(ORG_ID), AiActorRole.LAWYER);

    assertThatThrownBy(
            () ->
                service.propose(
                    "create_client", MAPPER.createObjectNode(), "Создать", outsideConversation))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void bindToMessageDelegatesToTheStoreAndMapsTheResultToDto() {
    AiActionProposal stored = proposal(AiActionProposalStatus.PENDING);
    stored.setMessageId("msg-1");
    LocalDateTime since = LocalDateTime.now(ZoneOffset.UTC);
    when(proposalStore.bindToMessage(LAWYER_ID, CONVERSATION_ID, since, "msg-1"))
        .thenReturn(List.of(stored));

    List<AiActionProposalResponse> responses =
        service.bindToMessage(LAWYER_ID, CONVERSATION_ID, since, "msg-1");

    assertThat(responses).hasSize(1);
    assertThat(responses.get(0).messageId()).isEqualTo("msg-1");
    assertThat(responses.get(0).id()).isEqualTo(stored.getId());
  }

  @Test
  void approveRunsTheToolAndRecordsItsResult() {
    AiActionProposal stored = proposal(AiActionProposalStatus.PENDING);
    when(proposalStore.claim(
            eq(stored.getId()), eq(LAWYER_ID), eq(AiActionProposalStatus.APPROVED), any()))
        .thenAnswer(claiming(stored));

    AiActionProposalResponse response = service.approve(stored.getId(), CONTEXT);

    assertThat(writeTool.performed).isEqualTo(1);
    assertThat(response.status()).isEqualTo("APPROVED");
    verify(proposalStore).recordSuccess(stored.getId(), "{\"clientId\":\"1\"}");
    verify(accessAuditService)
        .recordAgentAction(
            eq(LAWYER_ID),
            eq("LAWYER"),
            eq(AuditAction.AI_ACTION_APPROVE),
            eq(stored.getId()),
            eq("create_client"));
  }

  @Test
  void secondApproveDoesNotRunTheToolAgain() {
    AiActionProposal stored = proposal(AiActionProposalStatus.PENDING);
    when(proposalStore.claim(
            eq(stored.getId()), eq(LAWYER_ID), eq(AiActionProposalStatus.APPROVED), any()))
        .thenAnswer(claiming(stored))
        .thenThrow(new AiActionProposalNotPendingException(stored.getId()));

    service.approve(stored.getId(), CONTEXT);
    assertThatThrownBy(() -> service.approve(stored.getId(), CONTEXT))
        .isInstanceOf(AiActionProposalNotPendingException.class);

    assertThat(writeTool.performed).isEqualTo(1);
  }

  @Test
  void approveRunsTheToolWithTheActorFromTheRequestNotTheStoredArguments() {
    AiActionProposal stored = proposal(AiActionProposalStatus.PENDING);
    when(proposalStore.claim(any(), any(), any(), any())).thenAnswer(claiming(stored));

    service.approve(stored.getId(), CONTEXT);

    assertThat(writeTool.seenContext.userId()).isEqualTo(LAWYER_ID);
    assertThat(writeTool.seenContext.role()).isEqualTo(AiActorRole.LAWYER);
    assertThat(writeTool.seenContext.conversationId()).isEqualTo(CONVERSATION_ID);
  }

  @Test
  void failingToolLeavesTheProposalFailedInsteadOfApproved() {
    writeTool.failing = true;
    AiActionProposal stored = proposal(AiActionProposalStatus.PENDING);
    when(proposalStore.claim(any(), any(), any(), any())).thenAnswer(claiming(stored));

    AiActionProposalResponse response = service.approve(stored.getId(), CONTEXT);

    assertThat(response.status()).isEqualTo("FAILED");
    verify(proposalStore).recordFailure(eq(stored.getId()), any());
    verify(proposalStore, never()).recordSuccess(any(), any());
  }

  @Test
  void approveWithAlwaysAllowTrustsTheToolBeforeRunningIt() {
    AiActionProposal stored = proposal(AiActionProposalStatus.PENDING);
    when(proposalStore.claim(any(), any(), any(), any())).thenAnswer(claiming(stored));

    service.approve(stored.getId(), CONTEXT, true);

    verify(trustedToolRepository).save(any());
    verify(accessAuditService)
        .recordAgentAction(
            eq(LAWYER_ID),
            eq("LAWYER"),
            eq(AuditAction.AI_TOOL_TRUST_GRANT),
            eq(null),
            eq("create_client"));
  }

  @Test
  void approveWithoutAlwaysAllowDoesNotTrustTheTool() {
    AiActionProposal stored = proposal(AiActionProposalStatus.PENDING);
    when(proposalStore.claim(any(), any(), any(), any())).thenAnswer(claiming(stored));

    service.approve(stored.getId(), CONTEXT, false);

    verify(trustedToolRepository, never()).save(any());
  }

  @Test
  void revokeDeletesTheTrustRecordAndAudits() {
    service.revoke("create_client", CONTEXT);

    verify(trustedToolRepository).deleteByUserIdAndToolName(LAWYER_ID, "create_client");
    verify(accessAuditService)
        .recordAgentAction(
            eq(LAWYER_ID),
            eq("LAWYER"),
            eq(AuditAction.AI_TOOL_TRUST_REVOKE),
            eq(null),
            eq("create_client"));
  }

  @Test
  void rejectDecidesWithoutRunningTheTool() {
    AiActionProposal stored = proposal(AiActionProposalStatus.PENDING);
    when(proposalStore.claim(
            eq(stored.getId()), eq(LAWYER_ID), eq(AiActionProposalStatus.REJECTED), any()))
        .thenAnswer(claiming(stored));

    AiActionProposalResponse response = service.reject(stored.getId(), CONTEXT);

    assertThat(response.status()).isEqualTo("REJECTED");
    assertThat(writeTool.performed).isZero();
    verify(accessAuditService)
        .recordAgentAction(
            any(), any(), eq(AuditAction.AI_ACTION_REJECT), eq(stored.getId()), any());
  }

  @Test
  void approveOfAToolThatIsNoLongerAvailableFailsInsteadOfSilentlySucceeding() {
    AiActionProposal stored = proposal(AiActionProposalStatus.PENDING);
    when(proposalStore.claim(any(), any(), any(), any())).thenAnswer(claiming(stored));
    service =
        new AiActionProposalService(
            proposalStore,
            new AiToolRegistry(List.of()),
            PROPERTIES,
            accessAuditService,
            trustedToolRepository);

    AiActionProposalResponse response = service.approve(stored.getId(), CONTEXT);

    assertThat(response.status()).isEqualTo("FAILED");
    verify(proposalStore).recordFailure(eq(stored.getId()), any());
  }

  private static org.mockito.stubbing.Answer<AiActionProposal> claiming(AiActionProposal proposal) {
    return invocation -> {
      proposal.setStatus(invocation.getArgument(2));
      proposal.setDecidedAt(invocation.getArgument(3));
      return proposal;
    };
  }

  private AiActionProposal proposal(AiActionProposalStatus status) {
    LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
    AiActionProposal proposal =
        new AiActionProposal(
            ORG_ID,
            LAWYER_ID,
            CONVERSATION_ID,
            "create_client",
            MAPPER.createObjectNode().put("name", "Иванов"),
            "Создать клиента «Иванов»",
            now,
            now.plusMinutes(30));
    proposal.setStatus(status);
    setId(proposal);
    return proposal;
  }

  private static void setId(AiActionProposal proposal) {
    try {
      var field = AiActionProposal.class.getDeclaredField("id");
      field.setAccessible(true);
      field.set(proposal, UUID.randomUUID());
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }

  private static final class RecordingWriteTool implements AiWriteTool {

    private int performed;
    private boolean failing;
    private AiToolContext seenContext;

    @Override
    public String name() {
      return "create_client";
    }

    @Override
    public String description() {
      return "creates a client";
    }

    @Override
    public JsonNode parameters() {
      return MAPPER.createObjectNode().put("type", "object");
    }

    @Override
    public AiToolResult execute(JsonNode arguments, AiToolContext context) {
      return AiToolResult.ok("proposed");
    }

    @Override
    public String title(JsonNode arguments) {
      return "Создать клиента";
    }

    @Override
    public AiToolResult perform(JsonNode arguments, AiToolContext context) {
      performed++;
      seenContext = context;
      if (failing) {
        throw new IllegalStateException("клиент с таким ИНН уже есть");
      }
      return AiToolResult.ok("{\"clientId\":\"1\"}");
    }

    @Override
    public boolean availableFor(AiToolContext context) {
      return context.isLawyer();
    }
  }
}
