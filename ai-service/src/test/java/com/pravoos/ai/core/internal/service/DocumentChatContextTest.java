package com.pravoos.ai.core.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pravoos.ai.core.api.CaseAccessProvider;
import com.pravoos.ai.core.api.CaseContextProvider;
import com.pravoos.ai.core.internal.dto.ChatRequest;
import com.pravoos.ai.core.internal.model.mongo.Conversation;
import com.pravoos.ai.core.internal.repository.mongo.ConversationRepository;
import com.pravoos.ai.core.internal.repository.mongo.MessageRepository;
import com.pravoos.ai.document.api.DocumentAccess;
import com.pravoos.ai.document.api.DocumentChunkMatch;
import com.pravoos.ai.document.api.DocumentChunkMatches;
import com.pravoos.ai.document.api.DocumentRetrieval;
import com.pravoos.ai.document.api.DocumentSummaryView;
import com.pravoos.ai.llm.api.LlmClient;
import com.pravoos.ai.llm.api.LlmResult;
import com.pravoos.ai.llm.api.LlmUsage;
import com.pravoos.ai.shared.config.DocumentProperties;
import com.pravoos.ai.shared.exception.ChatScopeConflictException;
import com.pravoos.ai.shared.exception.ConversationDocumentMismatchException;
import com.pravoos.ai.shared.exception.DocumentNotFoundException;
import com.pravoos.ai.shared.model.enums.DocumentKind;
import com.pravoos.ai.shared.model.enums.DocumentStatus;
import com.pravoos.ai.shared.model.enums.DocumentSummaryStatus;
import com.pravoos.ai.shared.service.LlmQuotaService;
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

  @Mock private ConversationRepository conversationRepository;
  @Mock private MessageRepository messageRepository;
  @Mock private DocumentRetrieval documentRetrieval;
  @Mock private DocumentAccess documentAccess;
  @Mock private CaseAccessProvider caseAccessProvider;
  @Mock private CaseContextProvider caseContextProvider;
  @Mock private DocumentAccessGuard documentAccessGuard;
  @Mock private RagService ragService;
  @Mock private LlmClient llmClient;
  @Mock private LegalDomainGuard legalDomainGuard;
  @Mock private LlmQuotaService quotaService;
  @Mock private ThreadPoolTaskExecutor chatStreamExecutor;

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
            ragService,
            llmClient,
            properties,
            legalDomainGuard,
            quotaService,
            chatStreamExecutor,
            12000);

    when(llmClient.complete(anyString(), anyList(), anyString()))
        .thenReturn(new LlmResult("Ответ", new LlmUsage(1, 1, 2)));
    when(conversationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(messageRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(documentRetrieval.retrieveKnowledgeBase(anyString(), anyInt())).thenReturn(List.of());
    when(documentRetrieval.retrieveInDocument(anyList(), anyInt(), eq(documentId)))
        .thenReturn(DocumentChunkMatches.empty());
    when(documentAccessGuard.requireVisible(eq(documentId), eq(lawyerId), anyList()))
        .thenReturn(summaryView(DocumentSummaryStatus.NONE, null, List.of()));
    when(ragService.buildDocumentSystemPrompt(
            anyString(), anyString(), anyList(), any(Boolean.class)))
        .thenReturn("document prompt");
    when(ragService.buildSystemPrompt(anyList(), any(Boolean.class))).thenReturn("generic prompt");
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
                12L));

    var response =
        service.chat(
            new ChatRequest(null, "Какой срок оплаты?", null, null, documentId), lawyerId, orgIds);

    verify(documentAccessGuard, atLeastOnce())
        .requireVisible(eq(documentId), eq(lawyerId), anyList());
    verify(documentRetrieval).retrieveInDocument(anyList(), anyInt(), eq(documentId));
    verify(documentRetrieval, never()).retrieveForCase(anyString(), anyInt(), any());
    verify(ragService)
        .buildDocumentSystemPrompt(
            eq("Договор поставки"), eq(""), eq(List.of("пункт 4.1 договора")), eq(false));
    verify(quotaService).recordTokenUsage(lawyerId, 12L);
    assertThat(response.sources()).containsExactly("Документ: Договор поставки");
  }

  @Test
  void passesReadySummaryWithKeyPointsIntoPrompt() {
    when(documentAccessGuard.requireVisible(eq(documentId), eq(lawyerId), anyList()))
        .thenReturn(
            summaryView(
                DocumentSummaryStatus.READY, "Договор поставки товара", List.of("Срок 30 дней")));

    service.chat(new ChatRequest(null, "О чём договор?", null, null, documentId), lawyerId, orgIds);

    verify(ragService)
        .buildDocumentSystemPrompt(
            eq("Договор поставки"),
            eq("Договор поставки товара\n- Срок 30 дней"),
            anyList(),
            eq(false));
  }

  @Test
  void bindsNewConversationToDocument() {
    service.chat(new ChatRequest(null, "Вопрос", null, null, documentId), lawyerId, orgIds);

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
                    new ChatRequest(null, "Вопрос", null, null, documentId), lawyerId, orgIds))
        .isInstanceOf(DocumentNotFoundException.class);

    verify(documentRetrieval, never()).retrieveInDocument(anyList(), anyInt(), any());
  }

  @Test
  void rejectsConversationBoundToAnotherDocument() {
    Conversation conversation = new Conversation(lawyerId, "Беседа", null, UUID.randomUUID());
    when(conversationRepository.findById("c1")).thenReturn(Optional.of(conversation));

    assertThatThrownBy(
            () ->
                service.chat(
                    new ChatRequest("c1", "Вопрос", null, null, documentId), lawyerId, orgIds))
        .isInstanceOf(ConversationDocumentMismatchException.class);
  }

  @Test
  void rejectsSimultaneousCaseAndDocumentScope() {
    assertThatThrownBy(
            () ->
                service.chat(
                    new ChatRequest(null, "Вопрос", null, UUID.randomUUID(), documentId),
                    lawyerId,
                    orgIds))
        .isInstanceOf(ChatScopeConflictException.class);

    verify(documentRetrieval, never()).retrieveInDocument(anyList(), anyInt(), any());
  }

  @Test
  void listsConversationsScopedToDocument() {
    when(conversationRepository.findByLawyerIdAndDocumentIdOrderByCreatedAtDesc(
            eq(lawyerId), eq(documentId), any()))
        .thenReturn(org.springframework.data.domain.Page.empty());

    service.getConversations(lawyerId, null, null, documentId, orgIds, 0, 20);

    verify(conversationRepository)
        .findByLawyerIdAndDocumentIdOrderByCreatedAtDesc(eq(lawyerId), eq(documentId), any());
    verify(conversationRepository, never())
        .findByLawyerIdAndCaseIdIsNullAndDocumentIdIsNullOrderByCreatedAtDesc(any(), any());
  }
}
