package com.pravoos.ai.core.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pravoos.ai.core.api.AiActorRole;
import com.pravoos.ai.core.api.AiTool;
import com.pravoos.ai.core.api.AiToolContext;
import com.pravoos.ai.core.api.AiToolResult;
import com.pravoos.ai.core.api.CaseAccessProvider;
import com.pravoos.ai.core.api.CaseContextProvider;
import com.pravoos.ai.core.api.DocumentAccessGuard;
import com.pravoos.ai.core.api.PageContextResolver;
import com.pravoos.ai.core.internal.agent.AgentLoop;
import com.pravoos.ai.core.internal.agent.AgentMetrics;
import com.pravoos.ai.core.internal.agent.AgentProperties;
import com.pravoos.ai.core.internal.agent.AiToolRegistry;
import com.pravoos.ai.core.internal.dto.AiActionProposalResponse;
import com.pravoos.ai.core.internal.dto.ChatRequest;
import com.pravoos.ai.core.internal.dto.ChatStreamToolStep;
import com.pravoos.ai.core.internal.model.mongo.Conversation;
import com.pravoos.ai.core.internal.model.mongo.Message;
import com.pravoos.ai.core.internal.model.mongo.ToolStepDoc;
import com.pravoos.ai.core.internal.repository.mongo.ConversationRepository;
import com.pravoos.ai.core.internal.repository.mongo.MessageRepository;
import com.pravoos.ai.document.api.DocumentAccess;
import com.pravoos.ai.document.api.DocumentRetrieval;
import com.pravoos.ai.document.api.RetrievedChunks;
import com.pravoos.ai.llm.api.LlmClient;
import com.pravoos.ai.llm.api.LlmOptions;
import com.pravoos.ai.llm.api.LlmStreamResult;
import com.pravoos.ai.llm.api.LlmToolCall;
import com.pravoos.ai.llm.api.LlmUsage;
import com.pravoos.ai.recyclebin.api.RecycleBin;
import com.pravoos.ai.shared.config.DocumentProperties;
import com.pravoos.ai.shared.model.enums.MessageRole;
import com.pravoos.ai.shared.service.LlmQuotaService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChatAgentStreamTest {

  private static final AgentProperties AGENT_PROPERTIES =
      new AgentProperties(
          8, 12, 8000, 8000, Duration.ofSeconds(120), 120_000L, Duration.ofMinutes(30), 30);

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Mock private ConversationRepository conversationRepository;
  @Mock private MessageRepository messageRepository;
  @Mock private DocumentRetrieval documentRetrieval;
  @Mock private DocumentAccess documentAccess;
  @Mock private CaseAccessProvider caseAccessProvider;
  @Mock private CaseContextProvider caseContextProvider;
  @Mock private DocumentAccessGuard documentAccessGuard;
  @Mock private PageContextResolver pageContextResolver;
  @Mock private RagService ragService;
  @Mock private LlmClient llmClient;
  @Mock private LegalDomainGuard legalDomainGuard;
  @Mock private LlmQuotaService llmQuotaService;
  @Mock private ThreadPoolTaskExecutor chatStreamExecutor;
  @Mock private AiActionProposalService proposalService;
  @Mock private RecycleBin recycleBin;

  private final UUID lawyerId = UUID.randomUUID();
  private final UUID orgId = UUID.randomUUID();
  private final StubTool tool = new StubTool();

  @BeforeEach
  void setUp() {
    doAnswer(
            invocation -> {
              invocation.getArgument(0, Runnable.class).run();
              return null;
            })
        .when(chatStreamExecutor)
        .execute(any());
    when(documentRetrieval.retrieveKnowledgeBase(anyString(), anyInt()))
        .thenReturn(RetrievedChunks.empty());
    when(ragService.buildSystemPrompt(anyList(), anyBoolean(), anyString())).thenReturn("system");
    when(conversationRepository.save(any(Conversation.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(messageRepository.save(any(Message.class)))
        .thenAnswer(
            invocation -> {
              Message message = invocation.getArgument(0);
              ReflectionTestUtils.setField(message, "id", UUID.randomUUID().toString());
              return message;
            });
  }

  private ChatService service() {
    return new ChatService(
        conversationRepository,
        messageRepository,
        documentRetrieval,
        documentAccess,
        caseAccessProvider,
        caseContextProvider,
        documentAccessGuard,
        pageContextResolver,
        ragService,
        new AgentLoop(
            llmClient,
            new AiToolRegistry(List.of(tool)),
            AGENT_PROPERTIES,
            MAPPER,
            new AgentMetrics(new SimpleMeterRegistry())),
        new AgentMetrics(new SimpleMeterRegistry()),
        AGENT_PROPERTIES,
        new DocumentProperties("/tmp", 1000, 100, 5, 20000, 50, 200, 1_000_000L, 10),
        legalDomainGuard,
        llmQuotaService,
        proposalService,
        recycleBin,
        chatStreamExecutor,
        Runnable::run,
        12000);
  }

  @Test
  void passesRegisteredToolsToTheModel() {
    answerWithoutToolCalls();

    service().chatStream(request(), lawyerId, List.of(orgId), AiActorRole.LAWYER);

    ArgumentCaptor<LlmOptions> options = ArgumentCaptor.forClass(LlmOptions.class);
    verify(llmClient).streamComplete(anyString(), anyList(), anyString(), options.capture(), any());
    assertThat(options.getValue().tools()).extracting("name").containsExactly("get_case");
    assertThat(options.getValue().toolChoice()).isEqualTo(LlmOptions.TOOL_CHOICE_AUTO);
  }

  @Test
  void executesToolWithActorFromRequestNotFromModelArguments() {
    answerWithToolCall();

    service().chatStream(request(), lawyerId, List.of(orgId), AiActorRole.LAWYER);

    assertThat(tool.seenContext)
        .extracting(AiToolContext::userId, AiToolContext::orgIds, AiToolContext::role)
        .containsExactly(lawyerId, List.of(orgId), AiActorRole.LAWYER);
    assertThat(tool.seenContext.conversationId()).isNotNull();
  }

  @Test
  void emitsToolStepEventsBeforeDone() {
    answerWithToolCall();

    SseEmitter emitter =
        service().chatStream(request(), lawyerId, List.of(orgId), AiActorRole.LAWYER);

    List<String> events = eventNames(emitter);
    assertThat(events).containsSubsequence("tool_step", "tool_step", "done");
    assertThat(payloadsOfType(emitter, ChatStreamToolStep.class))
        .extracting(ChatStreamToolStep::name, ChatStreamToolStep::status)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("get_case", "RUNNING"),
            org.assertj.core.groups.Tuple.tuple("get_case", "OK"));
  }

  @Test
  void emitsProposalsRaisedInThisTurnBeforeDone() {
    answerWithToolCall();
    when(proposalService.bindToMessage(any(), anyString(), any(), anyString()))
        .thenReturn(List.of(proposalResponse()));

    SseEmitter emitter =
        service().chatStream(request(), lawyerId, List.of(orgId), AiActorRole.LAWYER);

    assertThat(eventNames(emitter)).containsSubsequence("tool_step", "proposal", "done");
    assertThat(payloadsOfType(emitter, AiActionProposalResponse.class))
        .extracting(AiActionProposalResponse::toolName, AiActionProposalResponse::status)
        .containsExactly(org.assertj.core.groups.Tuple.tuple("create_case", "PENDING"));
  }

  @Test
  void asksOnlyForProposalsOfTheCurrentUserAndConversation() {
    answerWithoutToolCalls();

    service().chatStream(request(), lawyerId, List.of(orgId), AiActorRole.LAWYER);

    ArgumentCaptor<String> conversationId = ArgumentCaptor.forClass(String.class);
    verify(proposalService)
        .bindToMessage(
            org.mockito.ArgumentMatchers.eq(lawyerId),
            conversationId.capture(),
            any(),
            anyString());
    assertThat(conversationId.getValue()).isNotBlank();
  }

  @Test
  void emitsNoProposalEventWhenTheTurnRaisedNone() {
    answerWithoutToolCalls();

    SseEmitter emitter =
        service().chatStream(request(), lawyerId, List.of(orgId), AiActorRole.LAWYER);

    assertThat(eventNames(emitter)).doesNotContain("proposal");
  }

  @Test
  void persistsToolStepsOnTheAssistantMessage() {
    answerWithToolCall();

    service().chatStream(request(), lawyerId, List.of(orgId), AiActorRole.LAWYER);

    ArgumentCaptor<Message> saved = ArgumentCaptor.forClass(Message.class);
    verify(messageRepository, org.mockito.Mockito.atLeastOnce()).save(saved.capture());
    Message assistant =
        saved.getAllValues().stream()
            .filter(message -> message.getRole() == MessageRole.ASSISTANT)
            .findFirst()
            .orElseThrow();
    assertThat(assistant.getToolSteps())
        .extracting(ToolStepDoc::getName, ToolStepDoc::getStatus)
        .containsExactly(org.assertj.core.groups.Tuple.tuple("get_case", "OK"));
  }

  @Test
  void keepsUserMessageFreeOfToolStepsWhenModelUsesNoTools() {
    answerWithoutToolCalls();

    service().chatStream(request(), lawyerId, List.of(orgId), AiActorRole.LAWYER);

    ArgumentCaptor<Message> saved = ArgumentCaptor.forClass(Message.class);
    verify(messageRepository, org.mockito.Mockito.atLeastOnce()).save(saved.capture());
    assertThat(saved.getAllValues())
        .allSatisfy(message -> assertThat(message.getToolSteps()).isEmpty());
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 2})
  void recordsAccumulatedTokenUsageOnce(int iterations) {
    if (iterations == 1) {
      answerWithoutToolCalls();
    } else {
      answerWithToolCall();
    }

    service().chatStream(request(), lawyerId, List.of(orgId), AiActorRole.LAWYER);

    verify(llmQuotaService).recordUsage(lawyerId, iterations == 1 ? 3 : 33, iterations);
  }

  private void answerWithoutToolCalls() {
    when(llmClient.streamComplete(anyString(), anyList(), anyString(), any(), any()))
        .thenAnswer(emitting("ответ", new LlmStreamResult(new LlmUsage(1, 2, 3), List.of(), null)));
  }

  private void answerWithToolCall() {
    when(llmClient.streamComplete(anyString(), anyList(), anyString(), any(), any()))
        .thenAnswer(
            emitting(
                null,
                new LlmStreamResult(
                    new LlmUsage(10, 1, 11),
                    List.of(new LlmToolCall("call-1", "get_case", "{\"caseId\":\"x\"}")),
                    "tool_calls")))
        .thenAnswer(
            emitting("ответ", new LlmStreamResult(new LlmUsage(20, 2, 22), List.of(), null)));
  }

  @SuppressWarnings("unchecked")
  private static org.mockito.stubbing.Answer<LlmStreamResult> emitting(
      String token, LlmStreamResult result) {
    return invocation -> {
      if (token != null) {
        ((Consumer<String>) invocation.getArgument(4)).accept(token);
      }
      return result;
    };
  }

  private static AiActionProposalResponse proposalResponse() {
    LocalDateTime now = LocalDateTime.now();
    return new AiActionProposalResponse(
        UUID.randomUUID(),
        "conv-1",
        "msg-1",
        "create_case",
        "Завести дело",
        "PENDING",
        MAPPER.createObjectNode(),
        null,
        null,
        now,
        now.plusMinutes(30));
  }

  private static ChatRequest request() {
    return new ChatRequest(null, "Что по делу?", List.of(), null, null, null);
  }

  @SuppressWarnings("unchecked")
  private static List<Object> sentData(SseEmitter emitter) {
    if (emitter == null) {
      return List.of();
    }
    var attempts =
        (java.util.Set<ResponseBodyEmitter.DataWithMediaType>)
            ReflectionTestUtils.getField(emitter, "earlySendAttempts");
    return attempts == null
        ? List.of()
        : attempts.stream().map(ResponseBodyEmitter.DataWithMediaType::getData).toList();
  }

  private static List<String> eventNames(SseEmitter emitter) {
    return sentData(emitter).stream()
        .filter(String.class::isInstance)
        .map(String.class::cast)
        .filter(value -> value.startsWith("event:"))
        .map(value -> value.substring("event:".length()).lines().findFirst().orElse(""))
        .toList();
  }

  private static <T> List<T> payloadsOfType(SseEmitter emitter, Class<T> type) {
    return sentData(emitter).stream().filter(type::isInstance).map(type::cast).toList();
  }

  private static final class StubTool implements AiTool {

    private AiToolContext seenContext;

    @Override
    public String name() {
      return "get_case";
    }

    @Override
    public String description() {
      return "stub";
    }

    @Override
    public JsonNode parameters() {
      return MAPPER.createObjectNode().put("type", "object");
    }

    @Override
    public AiToolResult execute(JsonNode arguments, AiToolContext context) {
      seenContext = context;
      return AiToolResult.ok("{\"title\":\"Дело\"}");
    }
  }
}
