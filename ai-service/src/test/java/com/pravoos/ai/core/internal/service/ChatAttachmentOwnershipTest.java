package com.pravoos.ai.core.internal.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.pravoos.ai.core.api.CaseAccessProvider;
import com.pravoos.ai.core.api.CaseContextProvider;
import com.pravoos.ai.core.internal.dto.ChatRequest;
import com.pravoos.ai.core.internal.repository.mongo.ConversationRepository;
import com.pravoos.ai.core.internal.repository.mongo.MessageRepository;
import com.pravoos.ai.document.api.DocumentAccess;
import com.pravoos.ai.document.api.DocumentRef;
import com.pravoos.ai.document.api.DocumentRetrieval;
import com.pravoos.ai.llm.api.LlmClient;
import com.pravoos.ai.llm.api.LlmResult;
import com.pravoos.ai.llm.api.LlmUsage;
import com.pravoos.ai.shared.config.DocumentProperties;
import com.pravoos.ai.shared.exception.DocumentNotFoundException;
import com.pravoos.ai.shared.service.LlmQuotaService;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChatAttachmentOwnershipTest {

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

  @Test
  void acceptsOwnChatAttachment() {
    UUID documentId = UUID.randomUUID();
    when(documentAccess.findByIds(any()))
        .thenReturn(List.of(new DocumentRef(documentId, null, lawyerId, "Мой файл")));
    when(documentAccess.chunkContentsForDocuments(anyList())).thenReturn(List.of("текст файла"));
    when(documentRetrieval.retrieveKnowledgeBase(anyString(), anyInt())).thenReturn(List.of());
    when(ragService.buildSystemPrompt(anyList(), any(Boolean.class))).thenReturn("prompt");
    when(llmClient.complete(anyString(), anyList(), anyString()))
        .thenReturn(new LlmResult("Ответ", new LlmUsage(1, 1, 2)));
    when(conversationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(messageRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    service.chat(
        new ChatRequest(null, "Проверь договор", List.of(documentId), null, null),
        lawyerId,
        List.of());
  }

  @Test
  void rejectsChatAttachmentOfAnotherLawyer() {
    UUID documentId = UUID.randomUUID();
    when(documentAccess.findByIds(any()))
        .thenReturn(List.of(new DocumentRef(documentId, null, UUID.randomUUID(), "Чужой файл")));

    assertThatThrownBy(
            () ->
                service.chat(
                    new ChatRequest(null, "Проверь договор", List.of(documentId), null, null),
                    lawyerId,
                    List.of()))
        .isInstanceOf(DocumentNotFoundException.class);
  }

  @Test
  void rejectsKnowledgeBaseDocument() {
    UUID documentId = UUID.randomUUID();
    when(documentAccess.findByIds(any()))
        .thenReturn(
            List.of(new DocumentRef(documentId, null, UUID.randomUUID(), "Документ базы знаний")));

    assertThatThrownBy(
            () ->
                service.chat(
                    new ChatRequest(null, "Проверь договор", List.of(documentId), null, null),
                    lawyerId,
                    List.of()))
        .isInstanceOf(DocumentNotFoundException.class);
  }

  @Test
  void rejectsCaseDocumentOfAnotherLawyer() {
    UUID documentId = UUID.randomUUID();
    UUID caseId = UUID.randomUUID();
    when(documentAccess.findByIds(any()))
        .thenReturn(List.of(new DocumentRef(documentId, caseId, UUID.randomUUID(), "Чужое дело")));
    when(caseAccessProvider.retainCasesOwnedBy(Set.of(caseId), lawyerId)).thenReturn(Set.of());

    assertThatThrownBy(
            () ->
                service.chat(
                    new ChatRequest(null, "Проверь договор", List.of(documentId), null, null),
                    lawyerId,
                    List.of()))
        .isInstanceOf(DocumentNotFoundException.class);
  }
}
