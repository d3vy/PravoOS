package com.pravoos.ai.core.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pravoos.ai.core.api.CaseAccessProvider;
import com.pravoos.ai.core.api.CaseContext;
import com.pravoos.ai.core.api.CaseContextProvider;
import com.pravoos.ai.core.api.PageContextResolver;
import com.pravoos.ai.core.internal.dto.ChatRequest;
import com.pravoos.ai.core.internal.model.mongo.Conversation;
import com.pravoos.ai.core.internal.repository.mongo.ConversationRepository;
import com.pravoos.ai.core.internal.repository.mongo.MessageRepository;
import com.pravoos.ai.document.api.DocumentAccess;
import com.pravoos.ai.document.api.DocumentRetrieval;
import com.pravoos.ai.document.api.RetrievedChunk;
import com.pravoos.ai.document.api.RetrievedChunks;
import com.pravoos.ai.llm.api.LlmClient;
import com.pravoos.ai.llm.api.LlmResult;
import com.pravoos.ai.llm.api.LlmUsage;
import com.pravoos.ai.shared.config.DocumentProperties;
import com.pravoos.ai.shared.exception.CaseNotFoundException;
import com.pravoos.ai.shared.exception.ConversationCaseMismatchException;
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
class CaseChatContextTest {

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
  @Mock private ThreadPoolTaskExecutor chatStreamExecutor;

  private ChatService service;

  private final UUID lawyerId = UUID.randomUUID();
  private final UUID caseId = UUID.randomUUID();
  private final List<UUID> orgIds = List.of(UUID.randomUUID());

  @Mock private LlmQuotaService quotaService;

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
            llmClient,
            properties,
            legalDomainGuard,
            quotaService,
            chatStreamExecutor,
            Runnable::run,
            12000);

    when(llmClient.complete(anyString(), anyList(), anyString()))
        .thenReturn(new LlmResult("Ответ", new LlmUsage(1, 1, 2)));
    when(conversationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(messageRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(caseContextProvider.loadContext(eq(caseId), eq(lawyerId), anyList()))
        .thenReturn(new CaseContext("Карточка дела", "Хронология", "Задачи"));
    when(ragService.buildCaseSystemPrompt(
            anyString(), anyString(), anyString(), anyList(), any(Boolean.class), anyString()))
        .thenReturn("case prompt");
    when(ragService.buildSystemPrompt(anyList(), any(Boolean.class), anyString()))
        .thenReturn("generic prompt");
    when(documentRetrieval.retrieveKnowledgeBase(anyString(), anyInt()))
        .thenReturn(RetrievedChunks.empty());
  }

  @Test
  void usesCaseRetrievalAndCaseContextWhenCaseIdPresent() {
    when(documentRetrieval.retrieveForCase(anyString(), anyInt(), eq(caseId)))
        .thenReturn(
            new RetrievedChunks(
                List.of(
                    new RetrievedChunk(
                        "текст из документа дела",
                        "Договор поставки",
                        0.9,
                        false,
                        null,
                        null,
                        null)),
                0L));

    var response =
        service.chat(
            new ChatRequest(null, "Какие сроки по договору?", null, caseId, null, null),
            lawyerId,
            orgIds);

    verify(caseAccessProvider).assertCaseVisible(caseId, lawyerId, orgIds);
    verify(documentRetrieval).retrieveForCase(anyString(), anyInt(), eq(caseId));
    verify(ragService)
        .buildCaseSystemPrompt(
            eq("Карточка дела"),
            eq("Хронология"),
            eq("Задачи"),
            eq(List.of("текст из документа дела")),
            eq(false),
            anyString());
    verify(ragService, never()).buildSystemPrompt(anyList(), any(Boolean.class), anyString());
    assertThat(response.sources()).containsExactly("Материалы дела: Договор поставки");
  }

  @Test
  void bindsNewConversationToCase() {
    when(documentRetrieval.retrieveForCase(anyString(), anyInt(), eq(caseId)))
        .thenReturn(RetrievedChunks.empty());

    service.chat(
        new ChatRequest(null, "Вопрос по делу", null, caseId, null, null), lawyerId, orgIds);

    verify(conversationRepository)
        .save(ArgumentMatchers.argThat(conversation -> caseId.equals(conversation.getCaseId())));
  }

  @Test
  void rejectsCaseOfAnotherLawyer() {
    doThrow(new CaseNotFoundException(caseId))
        .when(caseAccessProvider)
        .assertCaseVisible(caseId, lawyerId, orgIds);

    assertThatThrownBy(
            () ->
                service.chat(
                    new ChatRequest(null, "Вопрос по чужому делу", null, caseId, null, null),
                    lawyerId,
                    orgIds))
        .isInstanceOf(CaseNotFoundException.class);

    verify(documentRetrieval, never()).retrieveForCase(anyString(), anyInt(), any());
  }

  @Test
  void rejectsConversationBoundToAnotherCase() {
    Conversation conversation = new Conversation(lawyerId, null, "Беседа", UUID.randomUUID(), null);
    when(conversationRepository.findActiveById("c1")).thenReturn(Optional.of(conversation));

    assertThatThrownBy(
            () ->
                service.chat(
                    new ChatRequest("c1", "Вопрос по делу", null, caseId, null, null),
                    lawyerId,
                    orgIds))
        .isInstanceOf(ConversationCaseMismatchException.class);
  }

  @Test
  void rejectsCaseMessageInGeneralConversation() {
    Conversation conversation = new Conversation(lawyerId, "Общая беседа");
    when(conversationRepository.findActiveById("c1")).thenReturn(Optional.of(conversation));

    assertThatThrownBy(
            () ->
                service.chat(
                    new ChatRequest("c1", "Вопрос по делу", null, caseId, null, null),
                    lawyerId,
                    orgIds))
        .isInstanceOf(ConversationCaseMismatchException.class);
  }

  @Test
  void generalChatKeepsKnowledgeBaseOnlyPath() {
    service.chat(new ChatRequest(null, "Общий вопрос", null, null, null, null), lawyerId, orgIds);

    verify(caseAccessProvider, never()).assertCaseVisible(any(), any(), anyList());
    verify(documentRetrieval, never()).retrieveForCase(anyString(), anyInt(), any());
    verify(ragService).buildSystemPrompt(anyList(), any(Boolean.class), anyString());
  }
}
