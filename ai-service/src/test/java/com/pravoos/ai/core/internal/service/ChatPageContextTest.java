package com.pravoos.ai.core.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pravoos.ai.core.api.AiActorRole;
import com.pravoos.ai.core.api.CaseAccessProvider;
import com.pravoos.ai.core.api.CaseContext;
import com.pravoos.ai.core.api.CaseContextProvider;
import com.pravoos.ai.core.api.DocumentAccessGuard;
import com.pravoos.ai.core.api.PageContextResolver;
import com.pravoos.ai.core.api.PageContextScope;
import com.pravoos.ai.core.internal.agent.AgentLoop;
import com.pravoos.ai.core.internal.agent.AgentMetrics;
import com.pravoos.ai.core.internal.agent.AgentProperties;
import com.pravoos.ai.core.internal.agent.AiToolRegistry;
import com.pravoos.ai.core.internal.dto.ChatRequest;
import com.pravoos.ai.core.internal.dto.PageContextRef;
import com.pravoos.ai.core.internal.repository.mongo.ConversationRepository;
import com.pravoos.ai.core.internal.repository.mongo.MessageRepository;
import com.pravoos.ai.document.api.DocumentAccess;
import com.pravoos.ai.document.api.DocumentChunkMatches;
import com.pravoos.ai.document.api.DocumentRetrieval;
import com.pravoos.ai.document.api.DocumentSummaryView;
import com.pravoos.ai.document.api.RetrievedChunks;
import com.pravoos.ai.llm.api.LlmClient;
import com.pravoos.ai.llm.api.LlmResult;
import com.pravoos.ai.llm.api.LlmUsage;
import com.pravoos.ai.recyclebin.api.RecycleBin;
import com.pravoos.ai.shared.config.DocumentProperties;
import com.pravoos.ai.shared.exception.DocumentNotFoundException;
import com.pravoos.ai.shared.model.enums.DocumentSummaryStatus;
import com.pravoos.ai.shared.service.LlmQuotaService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChatPageContextTest {

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
  private final UUID caseId = UUID.randomUUID();
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
    when(documentRetrieval.retrieveForCase(anyString(), anyInt(), any(), any()))
        .thenReturn(RetrievedChunks.empty());
    when(caseContextProvider.loadContext(any(), any(), anyList()))
        .thenReturn(new CaseContext("Карточка дела", "Хронология", "Задачи"));
    when(ragService.buildSystemPrompt(anyList(), any(Boolean.class), any()))
        .thenReturn("generic prompt");
    when(ragService.buildCaseSystemPrompt(
            anyString(), anyString(), anyString(), anyList(), any(Boolean.class), any()))
        .thenReturn("case prompt");
    when(ragService.buildDocumentSystemPrompt(
            anyString(), anyString(), anyList(), any(Boolean.class), any()))
        .thenReturn("document prompt");
  }

  private ChatRequest requestWithPageContext(String entityType, UUID entityId) {
    return new ChatRequest(
        null,
        "Что по срокам?",
        null,
        null,
        null,
        new PageContextRef("/route", entityType, entityId));
  }

  @Test
  void casePageContextReusesTheCaseScopeAndItsAccessCheck() {
    when(pageContextResolver.resolve("CASE", caseId, lawyerId, orgIds))
        .thenReturn(PageContextScope.ofCase(caseId, "Иванов против ООО Ромашка"));

    service.chat(requestWithPageContext("CASE", caseId), lawyerId, orgIds, AiActorRole.LAWYER);

    verify(caseAccessProvider).assertCaseVisible(caseId, lawyerId, orgIds);
    verify(documentRetrieval).retrieveForCase(anyString(), anyInt(), eq(caseId), any());
    verify(ragService, never()).buildSystemPrompt(anyList(), any(Boolean.class), anyString());
  }

  @Test
  void resolvedLabelReachesThePromptFenced() {
    when(pageContextResolver.resolve("CASE", caseId, lawyerId, orgIds))
        .thenReturn(PageContextScope.ofCase(caseId, "Иванов против ООО Ромашка"));

    service.chat(requestWithPageContext("CASE", caseId), lawyerId, orgIds, AiActorRole.LAWYER);

    ArgumentCaptor<String> pageContextLine = ArgumentCaptor.forClass(String.class);
    verify(ragService)
        .buildCaseSystemPrompt(
            anyString(),
            anyString(),
            anyString(),
            anyList(),
            any(Boolean.class),
            pageContextLine.capture());
    assertThat(pageContextLine.getValue())
        .contains("Иванов против ООО Ромашка")
        .contains("CASE id: " + caseId)
        .contains("<<<КОНТЕКСТ_СТРАНИЦЫ_НАЧАЛО>>>")
        .contains("<<<КОНТЕКСТ_СТРАНИЦЫ_КОНЕЦ>>>");
  }

  @Test
  void labelOnlyContextCarriesNoEntityIdIntoThePrompt() {
    UUID clientId = UUID.randomUUID();
    when(pageContextResolver.resolve("CLIENT", clientId, lawyerId, orgIds))
        .thenReturn(PageContextScope.ofLabel("ООО Ромашка"));

    service.chat(requestWithPageContext("CLIENT", clientId), lawyerId, orgIds, AiActorRole.LAWYER);

    ArgumentCaptor<String> pageContextLine = ArgumentCaptor.forClass(String.class);
    verify(ragService).buildSystemPrompt(anyList(), any(Boolean.class), pageContextLine.capture());
    assertThat(pageContextLine.getValue())
        .doesNotContain(clientId.toString())
        .doesNotContain("id:");
  }

  @Test
  void inaccessibleEntityIsDroppedInsteadOfFailingTheChat() {
    when(pageContextResolver.resolve("CASE", caseId, lawyerId, orgIds))
        .thenReturn(PageContextScope.none());

    service.chat(requestWithPageContext("CASE", caseId), lawyerId, orgIds, AiActorRole.LAWYER);

    verify(caseAccessProvider, never()).assertCaseVisible(any(), any(), anyList());
    verify(ragService).buildSystemPrompt(anyList(), any(Boolean.class), eq(""));
  }

  @Test
  void inaccessibleDocumentIsDroppedInsteadOfFailingTheChat() {
    when(documentAccessGuard.requireVisible(documentId, lawyerId, orgIds))
        .thenThrow(new DocumentNotFoundException(documentId));

    service.chat(
        requestWithPageContext("DOCUMENT", documentId), lawyerId, orgIds, AiActorRole.LAWYER);

    verify(ragService).buildSystemPrompt(anyList(), any(Boolean.class), eq(""));
  }

  @Test
  void documentPageContextScopesTheChatToThatDocument() {
    DocumentSummaryView document =
        new DocumentSummaryView(
            documentId,
            null,
            lawyerId,
            "Договор поставки",
            null,
            null,
            DocumentSummaryStatus.NONE,
            null,
            List.of(),
            null);
    when(documentAccessGuard.requireVisible(documentId, lawyerId, orgIds)).thenReturn(document);
    when(documentRetrieval.retrieveInDocument(anyList(), anyInt(), eq(documentId), any()))
        .thenReturn(DocumentChunkMatches.empty());

    service.chat(
        requestWithPageContext("DOCUMENT", documentId), lawyerId, orgIds, AiActorRole.LAWYER);

    verify(documentRetrieval).retrieveInDocument(anyList(), anyInt(), eq(documentId), any());
  }

  @Test
  void explicitScopeWinsOverPageContext() {
    ChatRequest request =
        new ChatRequest(
            null,
            "Что по срокам?",
            null,
            caseId,
            null,
            new PageContextRef("/clients/1", "CLIENT", UUID.randomUUID()));

    service.chat(request, lawyerId, orgIds, AiActorRole.LAWYER);

    verify(pageContextResolver, never()).resolve(anyString(), any(), any(), anyList());
    verify(documentRetrieval).retrieveForCase(anyString(), anyInt(), eq(caseId), any());
  }

  @Test
  void labelOnlyContextDoesNotChangeTheRetrievalScope() {
    UUID clientId = UUID.randomUUID();
    when(pageContextResolver.resolve("CLIENT", clientId, lawyerId, orgIds))
        .thenReturn(PageContextScope.ofLabel("ООО Ромашка"));

    service.chat(requestWithPageContext("CLIENT", clientId), lawyerId, orgIds, AiActorRole.LAWYER);

    verify(documentRetrieval, never()).retrieveForCase(anyString(), anyInt(), any(), any());
    ArgumentCaptor<String> pageContextLine = ArgumentCaptor.forClass(String.class);
    verify(ragService).buildSystemPrompt(anyList(), any(Boolean.class), pageContextLine.capture());
    assertThat(pageContextLine.getValue()).contains("ООО Ромашка");
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
