package com.pravoos.ai.core.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pravoos.ai.core.api.CaseAccessProvider;
import com.pravoos.ai.core.api.CaseContext;
import com.pravoos.ai.core.api.CaseContextProvider;
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
import com.pravoos.ai.llm.api.LlmClient;
import com.pravoos.ai.llm.api.LlmResult;
import com.pravoos.ai.llm.api.LlmUsage;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
  @Mock private LlmQuotaService llmQuotaService;
  @Mock private ThreadPoolTaskExecutor chatStreamExecutor;

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
            ragService,
            llmClient,
            properties,
            legalDomainGuard,
            llmQuotaService,
            chatStreamExecutor,
            12000);
  }

  private Conversation existingConversation(UUID caseId, UUID documentId) {
    Conversation conversation = new Conversation(lawyerId, "title", caseId, documentId);
    ReflectionTestUtils.setField(conversation, "id", UUID.randomUUID().toString());
    return conversation;
  }

  @Test
  void chatRejectsWhenCaseAndDocumentBothScoped() {
    ChatRequest request =
        new ChatRequest(null, "Вопрос", List.of(), UUID.randomUUID(), UUID.randomUUID());

    assertThatThrownBy(() -> service.chat(request, lawyerId, List.of()))
        .isInstanceOf(ChatScopeConflictException.class);
    verifyNoInteractions(llmQuotaService);
  }

  @Test
  void chatPropagatesQuotaExceeded() {
    ChatRequest request = new ChatRequest(null, "Вопрос", List.of(), null, null);
    doThrow(new LlmQuotaExceededException()).when(llmQuotaService).assertWithinQuota(lawyerId);

    assertThatThrownBy(() -> service.chat(request, lawyerId, List.of()))
        .isInstanceOf(LlmQuotaExceededException.class);
    verifyNoInteractions(llmClient);
  }

  @Test
  void chatPropagatesNonLegalQueryRejection() {
    ChatRequest request = new ChatRequest(null, "Погода", List.of(), null, null);
    doThrow(new NonLegalQueryException()).when(legalDomainGuard).assertLegalQuery("Погода");

    assertThatThrownBy(() -> service.chat(request, lawyerId, List.of()))
        .isInstanceOf(NonLegalQueryException.class);
    verifyNoInteractions(llmClient);
  }

  @Test
  void chatOnNewConversationBuildsPlainPromptAndPersists() {
    ChatRequest request = new ChatRequest(null, "Что такое иск?", List.of(), null, null);
    when(documentRetrieval.retrieveKnowledgeBase(anyString(), anyInt()))
        .thenReturn(
            List.of(new RetrievedChunk("контент", "Кодекс", 0.9, true, "ГК РФ", "15", null)));
    when(ragService.buildSystemPrompt(anyList(), eq(true))).thenReturn("system-prompt");
    when(llmClient.complete(eq("system-prompt"), anyList(), eq("Что такое иск?")))
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

    ChatResponse response = service.chat(request, lawyerId, List.of());

    assertThat(response.conversationId()).isEqualTo("conv-1");
    assertThat(response.answer()).isEqualTo("Ответ на вопрос");
    assertThat(response.sources()).containsExactly("ст. 15 ГК РФ");
    verify(llmQuotaService).recordUsage(lawyerId, 10);
    verify(conversationRepository).save(any());
    verify(messageRepository, org.mockito.Mockito.times(2)).save(any());
  }

  @Test
  void chatOnExistingConversationUsesHistoryAndDoesNotSaveConversationAgain() {
    Conversation conversation = existingConversation(null, null);
    ChatRequest request =
        new ChatRequest(conversation.getId(), "Продолжение", List.of(), null, null);
    when(conversationRepository.findById(conversation.getId()))
        .thenReturn(Optional.of(conversation));
    when(messageRepository.findTop10ByConversationIdOrderByCreatedAtDesc(conversation.getId()))
        .thenReturn(
            List.of(new Message(conversation.getId(), MessageRole.ASSISTANT, "Привет", List.of())));
    when(documentRetrieval.retrieveKnowledgeBase(anyString(), anyInt())).thenReturn(List.of());
    when(ragService.buildSystemPrompt(anyList(), eq(false))).thenReturn("system-prompt");
    when(llmClient.complete(eq("system-prompt"), anyList(), eq("Продолжение")))
        .thenReturn(new LlmResult("Ответ", new LlmUsage(1, 2, 3)));
    when(messageRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    ChatResponse response = service.chat(request, lawyerId, List.of());

    assertThat(response.conversationId()).isEqualTo(conversation.getId());
    verify(conversationRepository, never()).save(any());
  }

  @Test
  void chatWithCaseScopeBuildsCaseSystemPrompt() {
    UUID caseId = UUID.randomUUID();
    ChatRequest request = new ChatRequest(null, "Что по делу?", List.of(), caseId, null);
    when(documentRetrieval.retrieveKnowledgeBase(anyString(), anyInt())).thenReturn(List.of());
    when(documentRetrieval.retrieveForCase(anyString(), anyInt(), eq(caseId)))
        .thenReturn(
            List.of(new RetrievedChunk("контент", "Иск.pdf", 0.5, false, null, null, null)));
    when(caseContextProvider.loadContext(caseId, lawyerId, List.of()))
        .thenReturn(new CaseContext("card", "timeline", "checklist"));
    when(ragService.buildCaseSystemPrompt(
            eq("card"), eq("timeline"), eq("checklist"), anyList(), eq(false)))
        .thenReturn("case-prompt");
    when(llmClient.complete(eq("case-prompt"), anyList(), eq("Что по делу?")))
        .thenReturn(new LlmResult("Ответ по делу", new LlmUsage(2, 2, 4)));
    when(conversationRepository.save(any()))
        .thenAnswer(
            invocation -> {
              Conversation conversation = invocation.getArgument(0);
              ReflectionTestUtils.setField(conversation, "id", "conv-2");
              return conversation;
            });
    when(messageRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    ChatResponse response = service.chat(request, lawyerId, List.of());

    assertThat(response.sources()).containsExactly("Материалы дела: Иск.pdf");
    verify(caseAccessProvider).assertCaseVisible(caseId, lawyerId, List.of());
  }

  @Test
  void chatWithDocumentScopeUsesDocumentSummaryAndSource() {
    UUID documentId = UUID.randomUUID();
    ChatRequest request = new ChatRequest(null, "Разбери документ", List.of(), null, documentId);
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
    when(documentRetrieval.retrieveKnowledgeBase(anyString(), anyInt())).thenReturn(List.of());
    when(documentRetrieval.retrieveInDocument(anyList(), anyInt(), eq(documentId)))
        .thenReturn(
            new DocumentChunkMatches(
                List.of(new DocumentChunkMatch(UUID.randomUUID(), 0, "фрагмент", 0.7)), 5L));
    when(ragService.buildDocumentSystemPrompt(
            eq("Договор.pdf"), anyString(), anyList(), anyBoolean()))
        .thenReturn("doc-prompt");
    when(llmClient.complete(eq("doc-prompt"), anyList(), eq("Разбери документ")))
        .thenReturn(new LlmResult("Ответ по документу", new LlmUsage(3, 3, 6)));
    when(conversationRepository.save(any()))
        .thenAnswer(
            invocation -> {
              Conversation conversation = invocation.getArgument(0);
              ReflectionTestUtils.setField(conversation, "id", "conv-3");
              return conversation;
            });
    when(messageRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    ChatResponse response = service.chat(request, lawyerId, List.of());

    assertThat(response.sources()).containsExactly("Документ: Договор.pdf");
    verify(llmQuotaService).recordTokenUsage(lawyerId, 5L);
  }

  @Test
  void chatOnExistingConversationRejectsCaseMismatch() {
    Conversation conversation = existingConversation(UUID.randomUUID(), null);
    ChatRequest request =
        new ChatRequest(conversation.getId(), "Вопрос", List.of(), UUID.randomUUID(), null);
    when(conversationRepository.findById(conversation.getId()))
        .thenReturn(Optional.of(conversation));

    assertThatThrownBy(() -> service.chat(request, lawyerId, List.of()))
        .isInstanceOf(ConversationCaseMismatchException.class);
  }

  @Test
  void chatOnExistingConversationRejectsDocumentMismatch() {
    UUID documentId = UUID.randomUUID();
    Conversation conversation = existingConversation(null, documentId);
    ChatRequest request =
        new ChatRequest(conversation.getId(), "Вопрос", List.of(), null, UUID.randomUUID());
    when(conversationRepository.findById(conversation.getId()))
        .thenReturn(Optional.of(conversation));

    assertThatThrownBy(() -> service.chat(request, lawyerId, List.of()))
        .isInstanceOf(ConversationDocumentMismatchException.class);
  }

  @Test
  void chatOnMissingConversationThrowsNotFound() {
    ChatRequest request = new ChatRequest("missing-id", "Вопрос", List.of(), null, null);
    when(conversationRepository.findById("missing-id")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.chat(request, lawyerId, List.of()))
        .isInstanceOf(ConversationNotFoundException.class);
  }

  @Test
  void chatOnConversationOwnedByAnotherLawyerThrowsNotFound() {
    Conversation conversation = existingConversation(null, null);
    ChatRequest request = new ChatRequest(conversation.getId(), "Вопрос", List.of(), null, null);
    when(conversationRepository.findById(conversation.getId()))
        .thenReturn(Optional.of(conversation));

    assertThatThrownBy(() -> service.chat(request, UUID.randomUUID(), List.of()))
        .isInstanceOf(ConversationNotFoundException.class);
  }

  @Test
  void chatStreamRejectedByExecutorThrowsLlmException() {
    ChatRequest request = new ChatRequest(null, "Вопрос", List.of(), null, null);
    org.mockito.Mockito.doThrow(new org.springframework.core.task.TaskRejectedException("full"))
        .when(chatStreamExecutor)
        .execute(any());

    assertThatThrownBy(() -> service.chatStream(request, lawyerId, List.of()))
        .isInstanceOf(LlmException.class);
  }

  @Test
  void chatStreamSubmitsWorkToExecutor() {
    ChatRequest request = new ChatRequest(null, "Вопрос", List.of(), null, null);

    var emitter = service.chatStream(request, lawyerId, List.of());

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
    when(conversationRepository
            .findByLawyerIdAndCaseIdIsNullAndDocumentIdIsNullOrderByCreatedAtDesc(
                eq(lawyerId), any()))
        .thenReturn(page);

    Page<ConversationResponse> result =
        service.getConversations(lawyerId, null, null, null, List.of(), 0, 20);

    assertThat(result.getContent()).hasSize(1);
  }

  @Test
  void getConversationsWithQueryFiltersByTitle() {
    Page<Conversation> page = new PageImpl<>(List.of());
    when(conversationRepository
            .findByLawyerIdAndCaseIdIsNullAndDocumentIdIsNullAndTitleContainingIgnoreCaseOrderByCreatedAtDesc(
                eq(lawyerId), eq("иск"), any()))
        .thenReturn(page);

    service.getConversations(lawyerId, "  иск  ", null, null, List.of(), 0, 20);

    verify(conversationRepository)
        .findByLawyerIdAndCaseIdIsNullAndDocumentIdIsNullAndTitleContainingIgnoreCaseOrderByCreatedAtDesc(
            eq(lawyerId), eq("иск"), any());
  }

  @Test
  void getConversationsWithCaseIdChecksVisibilityAndFilters() {
    UUID caseId = UUID.randomUUID();
    Page<Conversation> page = new PageImpl<>(List.of());
    when(conversationRepository.findByLawyerIdAndCaseIdOrderByCreatedAtDesc(
            eq(lawyerId), eq(caseId), any()))
        .thenReturn(page);

    service.getConversations(lawyerId, null, caseId, null, List.of(), 0, 20);

    verify(caseAccessProvider).assertCaseVisible(caseId, lawyerId, List.of());
  }

  @Test
  void getConversationsWithDocumentIdChecksVisibility() {
    UUID documentId = UUID.randomUUID();
    Page<Conversation> page = new PageImpl<>(List.of());
    when(conversationRepository.findByLawyerIdAndDocumentIdOrderByCreatedAtDesc(
            eq(lawyerId), eq(documentId), any()))
        .thenReturn(page);

    service.getConversations(lawyerId, null, null, documentId, List.of(), 0, 20);

    verify(documentAccessGuard).requireVisible(documentId, lawyerId, List.of());
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
    when(conversationRepository.findById("conv-x")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.rateMessage("m1", new RateRequest(1, null), lawyerId))
        .isInstanceOf(MessageNotFoundException.class);
  }

  @Test
  void rateMessageThrowsWhenConversationOwnedByAnotherLawyer() {
    Conversation conversation = existingConversation(null, null);
    Message message = new Message(conversation.getId(), MessageRole.ASSISTANT, "text", List.of());
    ReflectionTestUtils.setField(message, "id", "m1");
    when(messageRepository.findById("m1")).thenReturn(Optional.of(message));
    when(conversationRepository.findById(conversation.getId()))
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
    when(conversationRepository.findById(conversation.getId()))
        .thenReturn(Optional.of(conversation));
    when(messageRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    MessageResponse response = service.rateMessage("m1", new RateRequest(-1, "плохо"), lawyerId);

    assertThat(response.rating()).isEqualTo(-1);
    assertThat(message.getRatingComment()).isEqualTo("плохо");
  }

  @Test
  void getMessagesThrowsWhenConversationMissing() {
    when(conversationRepository.findById("conv-x")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getMessages("conv-x", lawyerId, 0, 20))
        .isInstanceOf(ConversationNotFoundException.class);
  }

  @Test
  void getMessagesThrowsWhenOwnedByAnotherLawyer() {
    Conversation conversation = existingConversation(null, null);
    when(conversationRepository.findById(conversation.getId()))
        .thenReturn(Optional.of(conversation));

    assertThatThrownBy(() -> service.getMessages(conversation.getId(), UUID.randomUUID(), 0, 20))
        .isInstanceOf(ConversationNotFoundException.class);
  }

  @Test
  void getMessagesReturnsChronologicalOrder() {
    Conversation conversation = existingConversation(null, null);
    when(conversationRepository.findById(conversation.getId()))
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
}
