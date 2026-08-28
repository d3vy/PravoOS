package com.pravoos.ai.core.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pravoos.ai.core.api.AiActorRole;
import com.pravoos.ai.core.api.CaseAccessProvider;
import com.pravoos.ai.core.api.CaseContextProvider;
import com.pravoos.ai.core.api.PageContextResolver;
import com.pravoos.ai.core.internal.agent.AgentLoop;
import com.pravoos.ai.core.internal.agent.AgentMetrics;
import com.pravoos.ai.core.internal.agent.AgentProperties;
import com.pravoos.ai.core.internal.agent.AiToolRegistry;
import com.pravoos.ai.core.internal.dto.ChatRequest;
import com.pravoos.ai.core.internal.model.mongo.Conversation;
import com.pravoos.ai.core.internal.repository.mongo.ConversationRepository;
import com.pravoos.ai.core.internal.repository.mongo.MessageRepository;
import com.pravoos.ai.document.api.DocumentAccess;
import com.pravoos.ai.document.api.DocumentChunkMatch;
import com.pravoos.ai.document.api.DocumentChunkMatches;
import com.pravoos.ai.document.api.DocumentRetrieval;
import com.pravoos.ai.document.api.DocumentSummaryView;
import com.pravoos.ai.document.api.RetrievedChunks;
import com.pravoos.ai.llm.api.LlmClient;
import com.pravoos.ai.llm.api.LlmResult;
import com.pravoos.ai.llm.api.LlmUsage;
import com.pravoos.ai.recyclebin.api.RecycleBin;
import com.pravoos.ai.shared.config.DocumentProperties;
import com.pravoos.ai.shared.exception.ChatScopeConflictException;
import com.pravoos.ai.shared.exception.ConversationDocumentMismatchException;
import com.pravoos.ai.shared.exception.DocumentNotFoundException;
import com.pravoos.ai.shared.model.enums.DocumentKind;
import com.pravoos.ai.shared.model.enums.DocumentStatus;
import com.pravoos.ai.shared.model.enums.DocumentSummaryStatus;
import com.pravoos.ai.shared.service.LlmQuotaService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DocumentChatContextTest {

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
  @Mock private LlmQuotaService quotaService;
  @Mock private ThreadPoolTaskExecutor chatStreamExecutor;
  @Mock private AiActionProposalService proposalService;
  @Mock private RecycleBin recycleBin;

  private ChatService service;

  private final UUID lawyerId = UUID.randomUUID();
  private final UUID documentId = UUID.randomUUID();
  private final List<UUID> orgIds = List.of(UUID.randomUUID());

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
            new AgentMetrics(new SimpleMeterRegistry()),
            AGENT_PROPERTIES,
            properties,
            legalDomainGuard,
            quotaService,
            proposalService,
            recycleBin,
            chatStreamExecutor,
            Runnable::run,
            12000);

    when(llmClient.complete(anyString(), anyList(), anyString(), any()))
        .thenReturn(new LlmResult("Ответ", new LlmUsage(1, 1, 2)));
    when(conversationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(messageRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(documentRetrieval.retrieveKnowledgeBase(anyString(), anyInt()))
        .thenReturn(RetrievedChunks.empty());
    when(documentRetrieval.retrieveInDocument(anyList(), anyInt(), eq(documentId)))
        .thenReturn(DocumentChunkMatches.empty());
    when(documentAccessGuard.requireVisible(eq(documentId), eq(lawyerId), anyList()))
        .thenReturn(summaryView(DocumentSummaryStatus.NONE, null, List.of()));
    when(ragService.buildDocumentSystemPrompt(
            anyString(), anyString(), anyList(), any(Boolean.class), anyString()))
        .thenReturn("document prompt");
    when(ragService.buildSystemPrompt(anyList(), any(Boolean.class), anyString()))
        .thenReturn("generic prompt");
  }

  private DocumentSummaryView summaryView(
      DocumentSummaryStatus status, String summary, List<String> keyPoints) {
    return new DocumentSummaryView(
        documentId,
        null,
        lawyerId,
        "Договор поставки",
        DocumentKind.GENERAL,
        DocumentStatus.READY,
        status,
        summary,
        keyPoints,
        null);
  }

  @Test
  void retrievesInsideTheDocumentAndLabelsItAsSource() {
    when(documentRetrieval.retrieveInDocument(anyList(), anyInt(), eq(documentId)))
        .thenReturn(
            new DocumentChunkMatches(
                List.of(new DocumentChunkMatch(UUID.randomUUID(), 3, "пункт 4.1 договора", 0.8)),
                12L,
                0L));

    var response =
        service.chat(
            new ChatRequest(null, "Какой срок оплаты?", null, null, documentId, null),
            lawyerId,
            orgIds,
            AiActorRole.LAWYER);

    verify(documentAccessGuard, atLeastOnce())
        .requireVisible(eq(documentId), eq(lawyerId), anyList());
    verify(documentRetrieval).retrieveInDocument(anyList(), anyInt(), eq(documentId));
    verify(documentRetrieval, never()).retrieveForCase(anyString(), anyInt(), any());
    verify(ragService)
        .buildDocumentSystemPrompt(
            eq("Договор поставки"),
            eq(""),
            eq(List.of("пункт 4.1 договора")),
            eq(false),
            anyString());
    verify(quotaService).recordTokenUsage(lawyerId, 12L);
    assertThat(response.sources()).containsExactly("Документ: Договор поставки");
  }

  @Test
  void passesReadySummaryWithKeyPointsIntoPrompt() {
    when(documentAccessGuard.requireVisible(eq(documentId), eq(lawyerId), anyList()))
        .thenReturn(
            summaryView(
                DocumentSummaryStatus.READY, "Договор поставки товара", List.of("Срок 30 дней")));

    service.chat(
        new ChatRequest(null, "О чём договор?", null, null, documentId, null),
        lawyerId,
        orgIds,
        AiActorRole.LAWYER);

    verify(ragService)
        .buildDocumentSystemPrompt(
            eq("Договор поставки"),
            eq("Договор поставки товара\n- Срок 30 дней"),
            anyList(),
            eq(false),
            anyString());
  }

  @Test
  void bindsNewConversationToDocument() {
    service.chat(
        new ChatRequest(null, "Вопрос", null, null, documentId, null),
        lawyerId,
        orgIds,
        AiActorRole.LAWYER);

    verify(conversationRepository)
        .save(
            ArgumentMatchers.argThat(
                conversation -> documentId.equals(conversation.getDocumentId())));
  }

  @Test
  void rejectsInvisibleDocument() {
    doThrow(new DocumentNotFoundException(documentId))
        .when(documentAccessGuard)
        .requireVisible(eq(documentId), eq(lawyerId), anyList());

    assertThatThrownBy(
            () ->
                service.chat(
                    new ChatRequest(null, "Вопрос", null, null, documentId, null),
                    lawyerId,
                    orgIds,
                    AiActorRole.LAWYER))
        .isInstanceOf(DocumentNotFoundException.class);

    verify(documentRetrieval, never()).retrieveInDocument(anyList(), anyInt(), any());
  }

  @Test
  void rejectsConversationBoundToAnotherDocument() {
    Conversation conversation = new Conversation(lawyerId, null, "Беседа", null, UUID.randomUUID());
    when(conversationRepository.findActiveById("c1")).thenReturn(Optional.of(conversation));

    assertThatThrownBy(
            () ->
                service.chat(
                    new ChatRequest("c1", "Вопрос", null, null, documentId, null),
                    lawyerId,
                    orgIds,
                    AiActorRole.LAWYER))
        .isInstanceOf(ConversationDocumentMismatchException.class);
  }

  @Test
  void rejectsSimultaneousCaseAndDocumentScope() {
    assertThatThrownBy(
            () ->
                service.chat(
                    new ChatRequest(null, "Вопрос", null, UUID.randomUUID(), documentId, null),
                    lawyerId,
                    orgIds,
                    AiActorRole.LAWYER))
        .isInstanceOf(ChatScopeConflictException.class);

    verify(documentRetrieval, never()).retrieveInDocument(anyList(), anyInt(), any());
  }

  @Test
  void listsConversationsScopedToDocument() {
    when(conversationRepository.searchForLawyer(
            eq(lawyerId), isNull(), eq(documentId), isNull(), any()))
        .thenReturn(org.springframework.data.domain.Page.empty());

    service.getConversations(lawyerId, null, null, documentId, orgIds, 0, 20);

    verify(conversationRepository)
        .searchForLawyer(eq(lawyerId), isNull(), eq(documentId), isNull(), any());
    verify(conversationRepository, never())
        .searchForLawyer(eq(lawyerId), isNull(), isNull(), isNull(), any());
  }

  private AgentLoop agentLoop() {
    return new AgentLoop(
        llmClient,
        new AiToolRegistry(List.of()),
        AGENT_PROPERTIES,
        new ObjectMapper(),
        new AgentMetrics(new SimpleMeterRegistry()));
  }
}
