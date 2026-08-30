package com.pravoos.ai.core.internal.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pravoos.ai.core.api.AiActionProposals;
import com.pravoos.ai.core.api.AiActorRole;
import com.pravoos.ai.core.api.AiToolContext;
import com.pravoos.ai.core.api.ProposedAction;
import com.pravoos.ai.core.internal.controller.AiActionProposalController;
import com.pravoos.ai.core.internal.model.entity.AiActionProposal;
import com.pravoos.ai.core.internal.model.entity.AiActionProposalStatus;
import com.pravoos.ai.core.internal.repository.jpa.AiTrustedToolRepository;
import com.pravoos.ai.core.internal.service.AiActionProposalService;
import com.pravoos.ai.core.internal.service.AiActionProposalStore;
import com.pravoos.ai.llm.api.LlmClient;
import com.pravoos.ai.llm.api.LlmResult;
import com.pravoos.ai.llm.api.LlmToolCall;
import com.pravoos.ai.llm.api.LlmUsage;
import com.pravoos.ai.practice.internal.agent.CreateClientTool;
import com.pravoos.ai.practice.internal.dto.ClientResponse;
import com.pravoos.ai.practice.internal.dto.CreateClientRequest;
import com.pravoos.ai.practice.internal.service.ClientService;
import com.pravoos.ai.shared.exception.GlobalExceptionHandler;
import com.pravoos.ai.shared.model.enums.ClientType;
import com.pravoos.ai.shared.security.CallerContextArgumentResolver;
import com.pravoos.ai.shared.service.AccessAuditService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Проверяет стык, который слои по отдельности не покрывают (см. plans/refactoring.md, тест 9):
 * модель вызвала tool -&gt; создалось PENDING-предложение -&gt; approve по HTTP -&gt; вызывается
 * реальный ClientService.create. AgentLoop, AiToolRegistry, CreateClientTool и
 * AiActionProposalService в этом тесте настоящие; замоканы только листья (LlmClient, ClientService,
 * хранилище предложений).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AgentProposalStitchTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final AgentProperties AGENT_PROPERTIES =
      new AgentProperties(
          8, 12, 8000, 8000, Duration.ofSeconds(120), 120_000L, Duration.ofMinutes(30), 30);

  @Mock private LlmClient llmClient;
  @Mock private ClientService clientService;
  @Mock private AiActionProposalStore proposalStore;
  @Mock private AccessAuditService accessAuditService;
  @Mock private AiTrustedToolRepository trustedToolRepository;
  @Mock private Authentication authentication;

  private final UUID lawyerId = UUID.randomUUID();
  private final UUID orgId = UUID.randomUUID();
  private final String conversationId = "conv-1";
  private final UUID proposalId = UUID.randomUUID();

  private AgentLoop agentLoop;
  private AiActionProposalService proposalService;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    // AiToolRegistry(tools) -> CreateClientTool -> AiActionProposals -> AiActionProposalService
    // -> AiToolRegistry: в проде цикл разорван @Lazy (commit e15eaa3); здесь -
    // forward-reference обёрткой, так как объекты собираются вручную, а не через Spring.
    AiActionProposalService[] serviceRef = new AiActionProposalService[1];
    AiActionProposals lazyProposals =
        new AiActionProposals() {
          @Override
          public ProposedAction propose(
              String toolName,
              com.fasterxml.jackson.databind.JsonNode arguments,
              String title,
              AiToolContext context) {
            return serviceRef[0].propose(toolName, arguments, title, context);
          }

          @Override
          public boolean isTrusted(String toolName, AiToolContext context) {
            return serviceRef[0].isTrusted(toolName, context);
          }
        };

    Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    CreateClientTool tool = new CreateClientTool(lazyProposals, validator, clientService, MAPPER);
    AiToolRegistry registry = new AiToolRegistry(List.of(tool));

    proposalService =
        new AiActionProposalService(
            proposalStore,
            registry,
            AGENT_PROPERTIES,
            accessAuditService,
            trustedToolRepository,
            new AgentMetrics(new SimpleMeterRegistry()));
    serviceRef[0] = proposalService;

    agentLoop =
        new AgentLoop(
            llmClient,
            registry,
            AGENT_PROPERTIES,
            MAPPER,
            new AgentMetrics(new SimpleMeterRegistry()));

    AiActionProposalController controller = new AiActionProposalController(proposalService);
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setCustomArgumentResolvers(new CallerContextArgumentResolver())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  void modelToolCallCreatesProposalAndApprovalRunsTheRealService() throws Exception {
    AiToolContext context =
        new AiToolContext(lawyerId, List.of(orgId), AiActorRole.LAWYER, conversationId);

    when(proposalStore.countCreatedSince(eq(lawyerId), any())).thenReturn(0L);
    when(proposalStore.create(any(), any(), any(), any(), any(), any(), any(), any()))
        .thenAnswer(
            invocation -> {
              AiActionProposal stored =
                  new AiActionProposal(
                      invocation.getArgument(0),
                      invocation.getArgument(1),
                      invocation.getArgument(2),
                      invocation.getArgument(3),
                      invocation.getArgument(4),
                      invocation.getArgument(5),
                      invocation.getArgument(6),
                      invocation.getArgument(7));
              ReflectionTestUtils.setField(stored, "id", proposalId);
              return stored;
            });

    LlmUsage usage = new LlmUsage(5, 5, 10);
    when(llmClient.complete(anyString(), anyList(), anyString(), any()))
        .thenReturn(
            new LlmResult(
                null,
                usage,
                List.of(
                    new LlmToolCall(
                        "call-1",
                        "create_client",
                        "{\"name\":\"Иванов И.И.\",\"type\":\"INDIVIDUAL\","
                            + "\"personalDataConsent\":true}")),
                null))
        .thenReturn(new LlmResult("Отправил предложение на подтверждение.", usage));

    // Модель вызвала tool -> предложение создано, реальный сервис клиента ещё не тронут.
    AgentResult result =
        agentLoop.run("system", List.of(), "Заведи клиента Иванова", context, step -> {});

    assertThat(result.steps()).hasSize(1);
    assertThat(result.steps().get(0).status()).isEqualTo(ToolStepStatus.OK);
    verify(proposalStore)
        .create(
            eq(orgId),
            eq(lawyerId),
            eq(conversationId),
            eq("create_client"),
            argThat(arguments -> arguments.get("name").asText().equals("Иванов И.И.")),
            anyString(),
            any(),
            any());
    verifyNoInteractions(clientService);

    // Approve по HTTP -> реальный ClientService.create вызван с распарсенными аргументами.
    when(proposalStore.claim(
            eq(proposalId), eq(lawyerId), eq(AiActionProposalStatus.APPROVED), any()))
        .thenAnswer(
            invocation -> {
              AiActionProposal stored =
                  new AiActionProposal(
                      orgId,
                      lawyerId,
                      conversationId,
                      "create_client",
                      MAPPER
                          .createObjectNode()
                          .put("name", "Иванов И.И.")
                          .put("type", "INDIVIDUAL")
                          .put("personalDataConsent", true),
                      "Создать клиента «Иванов И.И.» (Физическое лицо)",
                      LocalDateTime.now(ZoneOffset.UTC),
                      LocalDateTime.now(ZoneOffset.UTC).plusMinutes(30));
              ReflectionTestUtils.setField(stored, "id", proposalId);
              stored.setStatus(invocation.getArgument(2));
              stored.setDecidedAt(invocation.getArgument(3));
              return stored;
            });
    UUID clientId = UUID.randomUUID();
    when(clientService.create(any(CreateClientRequest.class), eq(lawyerId)))
        .thenReturn(
            new ClientResponse(
                clientId,
                "Иванов И.И.",
                ClientType.INDIVIDUAL,
                ClientType.INDIVIDUAL.getDisplayName(),
                null,
                null,
                null,
                null,
                LocalDateTime.now(ZoneOffset.UTC),
                0));

    when(authentication.getPrincipal()).thenReturn(lawyerId.toString());
    doReturn(List.of(new SimpleGrantedAuthority("ROLE_LAWYER")))
        .when(authentication)
        .getAuthorities();

    mockMvc
        .perform(
            post("/api/ai/chat/proposals/{proposalId}/approve", proposalId)
                .principal(authentication))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("APPROVED"));

    verify(clientService)
        .create(argThat(request -> request.name().equals("Иванов И.И.")), eq(lawyerId));
    verify(proposalStore).recordSuccess(eq(proposalId), anyString());
  }
}
