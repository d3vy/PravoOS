package com.pravoos.ai.core.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pravoos.ai.core.api.AiActorRole;
import com.pravoos.ai.core.api.CaseAccessProvider;
import com.pravoos.ai.core.api.CaseContext;
import com.pravoos.ai.core.api.CaseContextProvider;
import com.pravoos.ai.core.api.PageContextResolver;
import com.pravoos.ai.core.internal.agent.AgentLoop;
import com.pravoos.ai.core.internal.agent.AgentProperties;
import com.pravoos.ai.core.internal.agent.AiToolRegistry;
import com.pravoos.ai.core.internal.dto.AiActionProposalResponse;
import com.pravoos.ai.core.internal.dto.ChatRequest;
import com.pravoos.ai.core.internal.dto.ChatResponse;
import com.pravoos.ai.core.internal.dto.ConversationResponse;
import com.pravoos.ai.core.internal.dto.MessageResponse;
import com.pravoos.ai.core.internal.dto.RateRequest;
import com.pravoos.ai.core.internal.model.mongo.Conversation;
import com.pravoos.ai.core.internal.model.mongo.Message;
import com.pravoos.ai.core.internal.repository.mongo.ConversationRepository;
import com.pravoos.ai.core.internal.repository.mongo.MessageRepository;
import com.pravoos.ai.document.api.DocumentAccess;
import com.pravoos.ai.document.api.DocumentChunkMatch;
import com.pravoos.ai.document.api.DocumentChunkMatches;
import com.pravoos.ai.document.api.DocumentRetrieval;
import com.pravoos.ai.document.api.DocumentSummaryView;
import com.pravoos.ai.document.api.RetrievedChunk;
import com.pravoos.ai.document.api.RetrievedChunks;
import com.pravoos.ai.llm.api.LlmClient;
import com.pravoos.ai.llm.api.LlmResult;
import com.pravoos.ai.llm.api.LlmUsage;
import com.pravoos.ai.recyclebin.api.DeletionActor;
import com.pravoos.ai.recyclebin.api.DeletionRole;
import com.pravoos.ai.recyclebin.api.RecycleBin;
import com.pravoos.ai.recyclebin.api.RecycleBinEntityType;
import com.pravoos.ai.shared.config.DocumentProperties;
import com.pravoos.ai.shared.exception.ChatScopeConflictException;
import com.pravoos.ai.shared.exception.ConversationCaseMismatchException;
import com.pravoos.ai.shared.exception.ConversationDocumentMismatchException;
import com.pravoos.ai.shared.exception.ConversationNotFoundException;
import com.pravoos.ai.shared.exception.LlmException;
import com.pravoos.ai.shared.exception.LlmQuotaExceededException;
import com.pravoos.ai.shared.exception.MessageNotFoundException;
import com.pravoos.ai.shared.exception.NonLegalQueryException;
import com.pravoos.ai.shared.model.enums.DocumentSummaryStatus;
import com.pravoos.ai.shared.model.enums.MessageRole;
import com.pravoos.ai.shared.service.LlmQuotaService;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChatServiceTest {

  private static final AgentProperties AGENT_PROPERTIES =
      new AgentProperties(
          8, 12, 8000, 8000, Duration.ofSeconds(120), 120_000L, Duration.ofMinutes(30), 30);

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

  private ChatService service;

  private final UUID lawyerId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    DocumentProperties properties =
        new DocumentProperties("/tmp", 1000, 100, 5, 20000, 50, 200, 1_000_000L, 10);
    service =
        new ChatService(
            conversationRepository,
            messageRepository,
            documentRetrieval,
            documentAccess,
            caseAccessProvider,
            caseContextProvider,
            documentAccessGuard,
            pageContextResolver,
            ragService,
            agentLoop(),
            AGENT_PROPERTIES,
            properties,
            legalDomainGuard,
            llmQuotaService,
            proposalService,
            recycleBin,
            chatStreamExecutor,
            Runnable::run,
            12000);
  }

  private ChatService serviceWith(Executor contextExecutor) {
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
        agentLoop(),
        AGENT_PROPERTIES,
        new DocumentProperties("/tmp", 1000, 100, 5, 20000, 50, 200, 1_000_000L, 10),
        legalDomainGuard,
        llmQuotaService,
        proposalService,
        recycleBin,
        chatStreamExecutor,
        contextExecutor,
        12000);
  }

  @Test
  void buildsSameCaseContextWhenBranchesRunOnAnotherThread() {
    UUID caseId = UUID.randomUUID();
    ChatRequest request = new ChatRequest(null, "Вопрос по делу", List.of(), caseId, null, null);
    when(caseContextProvider.loadContext(eq(caseId), eq(lawyerId), anyList()))
        .thenReturn(new CaseContext("Карточка", "Хронология", "Задачи"));
    when(documentRetrieval.retrieveKnowledgeBase(anyString(), anyInt()))
        .thenReturn(new RetrievedChunks(List.of(), 7L));
    when(documentRetrieval.retrieveForCase(anyString(), anyInt(), eq(caseId)))
        .thenReturn(
            new RetrievedChunks(
                List.of(new RetrievedChunk("фрагмент", "Иск.pdf", 0.8, false, null, null, null)),
                4L));
    when(ragService.buildCaseSystemPrompt(
            eq("Карточка"), eq("Хронология"), eq("Задачи"), anyList(), anyBoolean(), anyString()))
        .thenReturn("case-prompt");
    when(llmClient.complete(eq("case-prompt"), anyList(), eq("Вопрос по делу"), any()))
        .thenReturn(new LlmResult("Ответ", new LlmUsage(1, 1, 2)));
    when(conversationRepository.save(any()))
        .thenAnswer(
            invocation -> {
              Conversation conversation = invocation.getArgument(0);
              ReflectionTestUtils.setField(conversation, "id", "conv-parallel");
              return conversation;
            });
    when(messageRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    ExecutorService contextExecutor = Executors.newFixedThreadPool(3);

    try {
      ChatResponse response =
          serviceWith(contextExecutor).chat(request, lawyerId, List.of(), AiActorRole.LAWYER);

      assertThat(response.sources()).containsExactly("Материалы дела: Иск.pdf");
      verify(llmQuotaService).recordTokenUsage(lawyerId, 7L);
      verify(llmQuotaService).recordTokenUsage(lawyerId, 4L);
    } finally {
      contextExecutor.shutdownNow();
    }
  }

  @Test
  void unwrapsDomainExceptionRaisedInsideParallelBranch() {
    ChatRequest request = new ChatRequest(null, "Вопрос", List.of(), null, null, null);
    when(documentRetrieval.retrieveKnowledgeBase(anyString(), anyInt()))
        .thenThrow(new LlmException("реранкер недоступен"));
    ExecutorService contextExecutor = Executors.newFixedThreadPool(3);

    try {
      ChatService parallelService = serviceWith(contextExecutor);

      assertThatThrownBy(
              () -> parallelService.chat(request, lawyerId, List.of(), AiActorRole.LAWYER))
          .isInstanceOf(LlmException.class)
          .hasMessage("реранкер недоступен");
      verifyNoInteractions(llmClient);
    } finally {
      contextExecutor.shutdownNow();
    }
  }

  private Conversation existingConversation(UUID caseId, UUID documentId) {
    Conversation conversation = new Conversation(lawyerId, null, "title", caseId, documentId);
    ReflectionTestUtils.setField(conversation, "id", UUID.randomUUID().toString());
    return conversation;
  }

  @Test
  void chatRejectsWhenCaseAndDocumentBothScoped() {
    ChatRequest request =
        new ChatRequest(null, "Вопрос", List.of(), UUID.randomUUID(), UUID.randomUUID(), null);

    assertThatThrownBy(() -> service.chat(request, lawyerId, List.of(), AiActorRole.LAWYER))
        .isInstanceOf(ChatScopeConflictException.class);
    verifyNoInteractions(llmQuotaService);
  }

  @Test
  void chatPropagatesQuotaExceeded() {
    ChatRequest request = new ChatRequest(null, "Вопрос", List.of(), null, null, null);
    doThrow(new LlmQuotaExceededException())
        .when(llmQuotaService)
        .assertQuotaHeadroom(eq(lawyerId), anyInt());

    assertThatThrownBy(() -> service.chat(request, lawyerId, List.of(), AiActorRole.LAWYER))
        .isInstanceOf(LlmQuotaExceededException.class);
    verifyNoInteractions(llmClient);
  }

  @Test
  void chatPropagatesNonLegalQueryRejection() {
    ChatRequest request = new ChatRequest(null, "Погода", List.of(), null, null, null);
    doThrow(new NonLegalQueryException()).when(legalDomainGuard).assertLegalQuery("Погода");

    assertThatThrownBy(() -> service.chat(request, lawyerId, List.of(), AiActorRole.LAWYER))
        .isInstanceOf(NonLegalQueryException.class);
    verifyNoInteractions(llmClient);
  }

  @Test
  void chatOnNewConversationBuildsPlainPromptAndPersists() {
    ChatRequest request = new ChatRequest(null, "Что такое иск?", List.of(), null, null, null);
    when(documentRetrieval.retrieveKnowledgeBase(anyString(), anyInt()))
        .thenReturn(
            new RetrievedChunks(
                List.of(new RetrievedChunk("контент", "Кодекс", 0.9, true, "ГК РФ", "15", null)),
                0L));
    when(ragService.buildSystemPrompt(anyList(), eq(true), anyString()))
        .thenReturn("system-prompt");
    when(llmClient.complete(eq("system-prompt"), anyList(), eq("Что такое иск?"), any()))
        .thenReturn(new LlmResult("Ответ на вопрос", new LlmUsage(5, 5, 10)));
    when(conversationRepository.save(any()))
        .thenAnswer(
            invocation -> {
              Conversation conversation = invocation.getArgument(0);
              ReflectionTestUtils.setField(conversation, "id", "conv-1");
              return conversation;
            });
    when(messageRepository.save(any()))
        .thenAnswer(
            invocation -> {
              Message message = invocation.getArgument(0);
              ReflectionTestUtils.setField(message, "id", UUID.randomUUID().toString());
              return message;
            });

    ChatResponse response = service.chat(request, lawyerId, List.of(), AiActorRole.LAWYER);

    assertThat(response.conversationId()).isEqualTo("conv-1");
    assertThat(response.answer()).isEqualTo("Ответ на вопрос");
    assertThat(response.sources()).containsExactly("ст. 15 ГК РФ");
    verify(llmQuotaService).recordUsage(lawyerId, 10, 1);
    verify(conversationRepository).save(any());
    verify(messageRepository, org.mockito.Mockito.times(2)).save(any());
  }

  @Test
  void chatWithoutStreamingCarriesProposalsRaisedInThisTurn() {
    ChatRequest request = new ChatRequest(null, "Заведи дело", List.of(), null, null, null);
    when(documentRetrieval.retrieveKnowledgeBase(anyString(), anyInt()))
        .thenReturn(RetrievedChunks.empty());
    when(ragService.buildSystemPrompt(
            anyList(), org.mockito.ArgumentMatchers.anyBoolean(), anyString()))
        .thenReturn("system-prompt");
    when(llmClient.complete(anyString(), anyList(), anyString(), any()))
        .thenReturn(new LlmResult("Подготовил действие", new LlmUsage(5, 5, 10)));
    when(conversationRepository.save(any()))
        .thenAnswer(
            invocation -> {
              Conversation conversation = invocation.getArgument(0);
              ReflectionTestUtils.setField(conversation, "id", "conv-1");
              return conversation;
            });
    when(messageRepository.save(any()))
        .thenAnswer(
            invocation -> {
              Message message = invocation.getArgument(0);
              ReflectionTestUtils.setField(message, "id", UUID.randomUUID().toString());
              return message;
            });
    when(proposalService.bindToMessage(eq(lawyerId), eq("conv-1"), any(), anyString()))
        .thenReturn(List.of(pendingProposal()));

    ChatResponse response = service.chat(request, lawyerId, List.of(), AiActorRole.LAWYER);

    assertThat(response.proposals())
        .extracting(AiActionProposalResponse::toolName, AiActionProposalResponse::status)
        .containsExactly(org.assertj.core.groups.Tuple.tuple("create_case", "PENDING"));
  }

  @Test
  void chatWithoutProposalsReturnsAnEmptyListNotNull() {
    ChatRequest request = new ChatRequest(null, "Что такое иск?", List.of(), null, null, null);
    when(documentRetrieval.retrieveKnowledgeBase(anyString(), anyInt()))
        .thenReturn(RetrievedChunks.empty());
    when(ragService.buildSystemPrompt(
            anyList(), org.mockito.ArgumentMatchers.anyBoolean(), anyString()))
        .thenReturn("system-prompt");
    when(llmClient.complete(anyString(), anyList(), anyString(), any()))
        .thenReturn(new LlmResult("Ответ", new LlmUsage(5, 5, 10)));
    when(conversationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(messageRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    ChatResponse response = service.chat(request, lawyerId, List.of(), AiActorRole.LAWYER);

    assertThat(response.proposals()).isEmpty();
  }

  private static AiActionProposalResponse pendingProposal() {
    java.time.LocalDateTime now = java.time.LocalDateTime.now();
    return new AiActionProposalResponse(
        UUID.randomUUID(),
        "conv-1",
        "msg-1",
        "create_case",
        "Завести дело",
        "PENDING",
        new ObjectMapper().createObjectNode(),
        null,
        null,
        now,
        now.plusMinutes(30));
  }

  @Test
  void chatOnExistingConversationUsesHistoryAndDoesNotSaveConversationAgain() {
    Conversation conversation = existingConversation(null, null);
    ChatRequest request =
        new ChatRequest(conversation.getId(), "Продолжение", List.of(), null, null, null);
    when(conversationRepository.findActiveById(conversation.getId()))
        .thenReturn(Optional.of(conversation));
    when(messageRepository.findTop10ByConversationIdOrderByCreatedAtDesc(conversation.getId()))
        .thenReturn(
            List.of(new Message(conversation.getId(), MessageRole.ASSISTANT, "Привет", List.of())));
    when(documentRetrieval.retrieveKnowledgeBase(anyString(), anyInt()))
        .thenReturn(RetrievedChunks.empty());
    when(ragService.buildSystemPrompt(anyList(), eq(false), anyString()))
        .thenReturn("system-prompt");
    when(llmClient.complete(eq("system-prompt"), anyList(), eq("Продолжение"), any()))
        .thenReturn(new LlmResult("Ответ", new LlmUsage(1, 2, 3)));
    when(messageRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    ChatResponse response = service.chat(request, lawyerId, List.of(), AiActorRole.LAWYER);

    assertThat(response.conversationId()).isEqualTo(conversation.getId());
    verify(conversationRepository, never()).save(any());
  }

  @Test
  void chatWithCaseScopeBuildsCaseSystemPrompt() {
    UUID caseId = UUID.randomUUID();
    ChatRequest request = new ChatRequest(null, "Что по делу?", List.of(), caseId, null, null);
    when(documentRetrieval.retrieveKnowledgeBase(anyString(), anyInt()))
        .thenReturn(RetrievedChunks.empty());
    when(documentRetrieval.retrieveForCase(anyString(), anyInt(), eq(caseId)))
        .thenReturn(
            new RetrievedChunks(
                List.of(new RetrievedChunk("контент", "Иск.pdf", 0.5, false, null, null, null)),
                0L));
    when(caseContextProvider.loadContext(caseId, lawyerId, List.of()))
        .thenReturn(new CaseContext("card", "timeline", "checklist"));
    when(ragService.buildCaseSystemPrompt(
            eq("card"), eq("timeline"), eq("checklist"), anyList(), eq(false), anyString()))
        .thenReturn("case-prompt");
    when(llmClient.complete(eq("case-prompt"), anyList(), eq("Что по делу?"), any()))
        .thenReturn(new LlmResult("Ответ по делу", new LlmUsage(2, 2, 4)));
    when(conversationRepository.save(any()))
        .thenAnswer(
            invocation -> {
              Conversation conversation = invocation.getArgument(0);
              ReflectionTestUtils.setField(conversation, "id", "conv-2");
              return conversation;
            });
    when(messageRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    ChatResponse response = service.chat(request, lawyerId, List.of(), AiActorRole.LAWYER);

    assertThat(response.sources()).containsExactly("Материалы дела: Иск.pdf");
    verify(caseAccessProvider).assertCaseVisible(caseId, lawyerId, List.of());
  }

  @Test
  void chatWithDocumentScopeUsesDocumentSummaryAndSource() {
    UUID documentId = UUID.randomUUID();
    ChatRequest request =
        new ChatRequest(null, "Разбери документ", List.of(), null, documentId, null);
    DocumentSummaryView document =
        new DocumentSummaryView(
            documentId,
            null,
            lawyerId,
            "Договор.pdf",
            null,
            null,
            DocumentSummaryStatus.READY,
            "Краткое содержание",
            List.of("пункт 1"),
            null);
    when(documentAccessGuard.requireVisible(documentId, lawyerId, List.of())).thenReturn(document);
    when(documentRetrieval.retrieveKnowledgeBase(anyString(), anyInt()))
        .thenReturn(RetrievedChunks.empty());
    when(documentRetrieval.retrieveInDocument(anyList(), anyInt(), eq(documentId)))
        .thenReturn(
            new DocumentChunkMatches(
                List.of(new DocumentChunkMatch(UUID.randomUUID(), 0, "фрагмент", 0.7)), 5L, 0L));
    when(ragService.buildDocumentSystemPrompt(
            eq("Договор.pdf"), anyString(), anyList(), anyBoolean(), anyString()))
        .thenReturn("doc-prompt");
    when(llmClient.complete(eq("doc-prompt"), anyList(), eq("Разбери документ"), any()))
        .thenReturn(new LlmResult("Ответ по документу", new LlmUsage(3, 3, 6)));
    when(conversationRepository.save(any()))
        .thenAnswer(
            invocation -> {
              Conversation conversation = invocation.getArgument(0);
              ReflectionTestUtils.setField(conversation, "id", "conv-3");
              return conversation;
            });
    when(messageRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    ChatResponse response = service.chat(request, lawyerId, List.of(), AiActorRole.LAWYER);

    assertThat(response.sources()).containsExactly("Документ: Договор.pdf");
    verify(llmQuotaService).recordTokenUsage(lawyerId, 5L);
  }

  @Test
  void chatOnExistingConversationRejectsCaseMismatch() {
    Conversation conversation = existingConversation(UUID.randomUUID(), null);
    ChatRequest request =
        new ChatRequest(conversation.getId(), "Вопрос", List.of(), UUID.randomUUID(), null, null);
    when(conversationRepository.findActiveById(conversation.getId()))
        .thenReturn(Optional.of(conversation));

    assertThatThrownBy(() -> service.chat(request, lawyerId, List.of(), AiActorRole.LAWYER))
        .isInstanceOf(ConversationCaseMismatchException.class);
  }

  @Test
  void chatOnExistingConversationRejectsDocumentMismatch() {
    UUID documentId = UUID.randomUUID();
    Conversation conversation = existingConversation(null, documentId);
    ChatRequest request =
        new ChatRequest(conversation.getId(), "Вопрос", List.of(), null, UUID.randomUUID(), null);
    when(conversationRepository.findActiveById(conversation.getId()))
        .thenReturn(Optional.of(conversation));

    assertThatThrownBy(() -> service.chat(request, lawyerId, List.of(), AiActorRole.LAWYER))
        .isInstanceOf(ConversationDocumentMismatchException.class);
  }

  @Test
  void chatOnMissingConversationThrowsNotFound() {
    ChatRequest request = new ChatRequest("missing-id", "Вопрос", List.of(), null, null, null);
    when(conversationRepository.findActiveById("missing-id")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.chat(request, lawyerId, List.of(), AiActorRole.LAWYER))
        .isInstanceOf(ConversationNotFoundException.class);
  }

  @Test
  void chatOnConversationOwnedByAnotherLawyerThrowsNotFound() {
    Conversation conversation = existingConversation(null, null);
    ChatRequest request =
        new ChatRequest(conversation.getId(), "Вопрос", List.of(), null, null, null);
    when(conversationRepository.findActiveById(conversation.getId()))
        .thenReturn(Optional.of(conversation));

    assertThatThrownBy(
            () -> service.chat(request, UUID.randomUUID(), List.of(), AiActorRole.LAWYER))
        .isInstanceOf(ConversationNotFoundException.class);
  }

  @Test
  void chatStreamRejectedByExecutorThrowsLlmException() {
    ChatRequest request = new ChatRequest(null, "Вопрос", List.of(), null, null, null);
    org.mockito.Mockito.doThrow(new org.springframework.core.task.TaskRejectedException("full"))
        .when(chatStreamExecutor)
        .execute(any());

    assertThatThrownBy(() -> service.chatStream(request, lawyerId, List.of(), AiActorRole.LAWYER))
        .isInstanceOf(LlmException.class);
  }

  @Test
  void chatStreamSubmitsWorkToExecutor() {
    ChatRequest request = new ChatRequest(null, "Вопрос", List.of(), null, null, null);

    var emitter = service.chatStream(request, lawyerId, List.of(), AiActorRole.LAWYER);

    assertThat(emitter).isNotNull();
    verify(chatStreamExecutor).execute(any());
  }

  @Test
  void getConversationsRejectsCaseAndDocumentBothSet() {
    assertThatThrownBy(
            () ->
                service.getConversations(
                    lawyerId, null, UUID.randomUUID(), UUID.randomUUID(), List.of(), 0, 20))
        .isInstanceOf(ChatScopeConflictException.class);
  }

  @Test
  void getConversationsWithoutScopeReturnsUnscopedPage() {
    Page<Conversation> page = new PageImpl<>(List.of(existingConversation(null, null)));
    when(conversationRepository.searchForLawyer(eq(lawyerId), isNull(), isNull(), isNull(), any()))
        .thenReturn(page);

    Page<ConversationResponse> result =
        service.getConversations(lawyerId, null, null, null, List.of(), 0, 20);

    assertThat(result.getContent()).hasSize(1);
  }

  @Test
  void getConversationsWithQueryFiltersByTitle() {
    Page<Conversation> page = new PageImpl<>(List.of());
    when(conversationRepository.searchForLawyer(
            eq(lawyerId), isNull(), isNull(), eq("  иск  "), any()))
        .thenReturn(page);

    service.getConversations(lawyerId, "  иск  ", null, null, List.of(), 0, 20);

    verify(conversationRepository)
        .searchForLawyer(eq(lawyerId), isNull(), isNull(), eq("  иск  "), any());
  }

  @Test
  void getConversationsWithCaseIdChecksVisibilityAndFilters() {
    UUID caseId = UUID.randomUUID();
    Page<Conversation> page = new PageImpl<>(List.of());
    when(conversationRepository.searchForLawyer(
            eq(lawyerId), eq(caseId), isNull(), isNull(), any()))
        .thenReturn(page);

    service.getConversations(lawyerId, null, caseId, null, List.of(), 0, 20);

    verify(caseAccessProvider).assertCaseVisible(caseId, lawyerId, List.of());
  }

  @Test
  void getConversationsWithDocumentIdChecksVisibility() {
    UUID documentId = UUID.randomUUID();
    Page<Conversation> page = new PageImpl<>(List.of());
    when(conversationRepository.searchForLawyer(
            eq(lawyerId), isNull(), eq(documentId), isNull(), any()))
        .thenReturn(page);

    service.getConversations(lawyerId, null, null, documentId, List.of(), 0, 20);

    verify(documentAccessGuard).requireVisible(documentId, lawyerId, List.of());
  }

  @Test
  void chatOnExistingConversationTouchesUpdatedAt() {
    Conversation conversation = existingConversation(null, null);
    ChatRequest request =
        new ChatRequest(conversation.getId(), "Продолжение", List.of(), null, null, null);
    when(conversationRepository.findActiveById(conversation.getId()))
        .thenReturn(Optional.of(conversation));
    when(documentRetrieval.retrieveKnowledgeBase(anyString(), anyInt()))
        .thenReturn(RetrievedChunks.empty());
    when(ragService.buildSystemPrompt(anyList(), eq(false), anyString()))
        .thenReturn("system-prompt");
    when(llmClient.complete(eq("system-prompt"), anyList(), eq("Продолжение"), any()))
        .thenReturn(new LlmResult("Ответ", new LlmUsage(1, 2, 3)));
    when(messageRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    service.chat(request, lawyerId, List.of(), AiActorRole.LAWYER);

    verify(conversationRepository).touch(eq(conversation.getId()), any());
  }

  @Test
  void newConversationTakesOrgIdFromSingleMembership() {
    UUID orgId = UUID.randomUUID();
    ChatRequest request = new ChatRequest(null, "Вопрос", List.of(), null, null, null);
    when(documentRetrieval.retrieveKnowledgeBase(anyString(), anyInt()))
        .thenReturn(RetrievedChunks.empty());
    when(ragService.buildSystemPrompt(anyList(), eq(false), anyString()))
        .thenReturn("system-prompt");
    when(llmClient.complete(anyString(), anyList(), anyString(), any()))
        .thenReturn(new LlmResult("Ответ", new LlmUsage(1, 2, 3)));
    when(conversationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(messageRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    service.chat(request, lawyerId, List.of(orgId), AiActorRole.LAWYER);

    ArgumentCaptor<Conversation> captor = ArgumentCaptor.forClass(Conversation.class);
    verify(conversationRepository).save(captor.capture());
    assertThat(captor.getValue().getOrgId()).isEqualTo(orgId);
  }

  @Test
  void newConversationWithoutSingleMembershipHasNoOrgId() {
    ChatRequest request = new ChatRequest(null, "Вопрос", List.of(), null, null, null);
    when(documentRetrieval.retrieveKnowledgeBase(anyString(), anyInt()))
        .thenReturn(RetrievedChunks.empty());
    when(ragService.buildSystemPrompt(anyList(), eq(false), anyString()))
        .thenReturn("system-prompt");
    when(llmClient.complete(anyString(), anyList(), anyString(), any()))
        .thenReturn(new LlmResult("Ответ", new LlmUsage(1, 2, 3)));
    when(conversationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(messageRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    service.chat(
        request, lawyerId, List.of(UUID.randomUUID(), UUID.randomUUID()), AiActorRole.LAWYER);

    ArgumentCaptor<Conversation> captor = ArgumentCaptor.forClass(Conversation.class);
    verify(conversationRepository).save(captor.capture());
    assertThat(captor.getValue().getOrgId()).isNull();
  }

  @Test
  void caseScopedConversationTakesOrgIdFromCase() {
    UUID caseId = UUID.randomUUID();
    UUID caseOrgId = UUID.randomUUID();
    ChatRequest request = new ChatRequest(null, "Что по делу?", List.of(), caseId, null, null);
    when(caseAccessProvider.caseOrgId(caseId)).thenReturn(caseOrgId);
    when(documentRetrieval.retrieveKnowledgeBase(anyString(), anyInt()))
        .thenReturn(RetrievedChunks.empty());
    when(documentRetrieval.retrieveForCase(anyString(), anyInt(), eq(caseId)))
        .thenReturn(RetrievedChunks.empty());
    when(caseContextProvider.loadContext(eq(caseId), eq(lawyerId), anyList()))
        .thenReturn(new CaseContext("card", "timeline", "checklist"));
    when(ragService.buildCaseSystemPrompt(
            anyString(), anyString(), anyString(), anyList(), anyBoolean(), anyString()))
        .thenReturn("case-prompt");
    when(llmClient.complete(anyString(), anyList(), anyString(), any()))
        .thenReturn(new LlmResult("Ответ", new LlmUsage(1, 2, 3)));
    when(conversationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(messageRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    service.chat(
        request, lawyerId, List.of(UUID.randomUUID(), UUID.randomUUID()), AiActorRole.LAWYER);

    ArgumentCaptor<Conversation> captor = ArgumentCaptor.forClass(Conversation.class);
    verify(conversationRepository).save(captor.capture());
    assertThat(captor.getValue().getOrgId()).isEqualTo(caseOrgId);
  }

  @Test
  void deleteConversationMovesItToRecycleBin() {
    Conversation owned = new Conversation(lawyerId, "Диалог");
    ReflectionTestUtils.setField(owned, "id", "conv-1");
    when(conversationRepository.findActiveById("conv-1")).thenReturn(Optional.of(owned));

    service.deleteConversation("conv-1", lawyerActor());

    verify(recycleBin).moveToBin(RecycleBinEntityType.CONVERSATION, "conv-1", lawyerActor());
  }

  @Test
  void deleteConversationThrowsWhenNotOwnedOrMissing() {
    Conversation foreign = new Conversation(UUID.randomUUID(), "Чужой диалог");
    when(conversationRepository.findActiveById("conv-1")).thenReturn(Optional.of(foreign));

    assertThatThrownBy(() -> service.deleteConversation("conv-1", lawyerActor()))
        .isInstanceOf(ConversationNotFoundException.class);
    verifyNoInteractions(recycleBin);
  }

  private DeletionActor lawyerActor() {
    return new DeletionActor(lawyerId, DeletionRole.LAWYER, null, List.of());
  }

  @Test
  void rateMessageThrowsWhenMessageMissing() {
    when(messageRepository.findById("m1")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.rateMessage("m1", new RateRequest(1, null), lawyerId))
        .isInstanceOf(MessageNotFoundException.class);
  }

  @Test
  void rateMessageThrowsWhenConversationMissing() {
    Message message = new Message("conv-x", MessageRole.ASSISTANT, "text", List.of());
    ReflectionTestUtils.setField(message, "id", "m1");
    when(messageRepository.findById("m1")).thenReturn(Optional.of(message));
    when(conversationRepository.findActiveById("conv-x")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.rateMessage("m1", new RateRequest(1, null), lawyerId))
        .isInstanceOf(MessageNotFoundException.class);
  }

  @Test
  void rateMessageThrowsWhenConversationOwnedByAnotherLawyer() {
    Conversation conversation = existingConversation(null, null);
    Message message = new Message(conversation.getId(), MessageRole.ASSISTANT, "text", List.of());
    ReflectionTestUtils.setField(message, "id", "m1");
    when(messageRepository.findById("m1")).thenReturn(Optional.of(message));
    when(conversationRepository.findActiveById(conversation.getId()))
        .thenReturn(Optional.of(conversation));

    assertThatThrownBy(() -> service.rateMessage("m1", new RateRequest(1, null), UUID.randomUUID()))
        .isInstanceOf(MessageNotFoundException.class);
  }

  @Test
  void rateMessageSavesRatingAndComment() {
    Conversation conversation = existingConversation(null, null);
    Message message = new Message(conversation.getId(), MessageRole.ASSISTANT, "text", List.of());
    ReflectionTestUtils.setField(message, "id", "m1");
    when(messageRepository.findById("m1")).thenReturn(Optional.of(message));
    when(conversationRepository.findActiveById(conversation.getId()))
        .thenReturn(Optional.of(conversation));
    when(messageRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    MessageResponse response = service.rateMessage("m1", new RateRequest(-1, "плохо"), lawyerId);

    assertThat(response.rating()).isEqualTo(-1);
    assertThat(message.getRatingComment()).isEqualTo("плохо");
  }

  @Test
  void getMessagesThrowsWhenConversationMissing() {
    when(conversationRepository.findActiveById("conv-x")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getMessages("conv-x", lawyerId, 0, 20))
        .isInstanceOf(ConversationNotFoundException.class);
  }

  @Test
  void getMessagesThrowsWhenOwnedByAnotherLawyer() {
    Conversation conversation = existingConversation(null, null);
    when(conversationRepository.findActiveById(conversation.getId()))
        .thenReturn(Optional.of(conversation));

    assertThatThrownBy(() -> service.getMessages(conversation.getId(), UUID.randomUUID(), 0, 20))
        .isInstanceOf(ConversationNotFoundException.class);
  }

  @Test
  void getMessagesReturnsChronologicalOrder() {
    Conversation conversation = existingConversation(null, null);
    when(conversationRepository.findActiveById(conversation.getId()))
        .thenReturn(Optional.of(conversation));
    Message newer = new Message(conversation.getId(), MessageRole.ASSISTANT, "новее", List.of());
    ReflectionTestUtils.setField(newer, "id", "m-newer");
    Message older = new Message(conversation.getId(), MessageRole.USER, "старее", List.of());
    ReflectionTestUtils.setField(older, "id", "m-older");
    Page<Message> page = new PageImpl<>(List.of(newer, older));
    when(messageRepository.findByConversationIdOrderByCreatedAtDesc(
            eq(conversation.getId()), any()))
        .thenReturn(page);

    Page<MessageResponse> result = service.getMessages(conversation.getId(), lawyerId, 0, 20);

    assertThat(result.getContent())
        .extracting(MessageResponse::id)
        .containsExactly("m-older", "m-newer");
  }

  private AgentLoop agentLoop() {
    return new AgentLoop(
        llmClient, new AiToolRegistry(List.of()), AGENT_PROPERTIES, new ObjectMapper());
  }
}
