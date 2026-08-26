package com.pravoos.ai.core.internal.service;

import com.pravoos.ai.core.api.CaseAccessProvider;
import com.pravoos.ai.core.api.CaseContext;
import com.pravoos.ai.core.api.CaseContextProvider;
import com.pravoos.ai.core.internal.dto.*;
import com.pravoos.ai.core.internal.model.mongo.Conversation;
import com.pravoos.ai.core.internal.model.mongo.Message;
import com.pravoos.ai.core.internal.repository.mongo.ConversationRepository;
import com.pravoos.ai.core.internal.repository.mongo.MessageRepository;
import com.pravoos.ai.document.api.DocumentAccess;
import com.pravoos.ai.document.api.DocumentChunkMatch;
import com.pravoos.ai.document.api.DocumentChunkMatches;
import com.pravoos.ai.document.api.DocumentRef;
import com.pravoos.ai.document.api.DocumentRetrieval;
import com.pravoos.ai.document.api.DocumentSummaryView;
import com.pravoos.ai.document.api.RetrievedChunk;
import com.pravoos.ai.document.api.RetrievedChunks;
import com.pravoos.ai.llm.api.LlmClient;
import com.pravoos.ai.llm.api.LlmMessage;
import com.pravoos.ai.llm.api.LlmResult;
import com.pravoos.ai.llm.api.LlmUsage;
import com.pravoos.ai.shared.config.DocumentProperties;
import com.pravoos.ai.shared.exception.ChatScopeConflictException;
import com.pravoos.ai.shared.exception.ConversationCaseMismatchException;
import com.pravoos.ai.shared.exception.ConversationDocumentMismatchException;
import com.pravoos.ai.shared.exception.ConversationNotFoundException;
import com.pravoos.ai.shared.exception.DocumentNotFoundException;
import com.pravoos.ai.shared.exception.LlmException;
import com.pravoos.ai.shared.exception.MessageNotFoundException;
import com.pravoos.ai.shared.model.enums.DocumentSummaryStatus;
import com.pravoos.ai.shared.model.enums.MessageRole;
import com.pravoos.ai.shared.service.LlmQuotaService;
import com.pravoos.ai.shared.util.Futures;
import com.pravoos.ai.shared.util.PageRequests;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class ChatService {

  private static final Logger log = LoggerFactory.getLogger(ChatService.class);
  private static final java.time.format.DateTimeFormatter EDITION_DATE_FORMAT =
      java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy");
  private static final int TITLE_MAX_LENGTH = 60;
  private static final String CASE_SOURCE_PREFIX = "Материалы дела: ";
  private static final String DOCUMENT_SOURCE_PREFIX = "Документ: ";
  private static final long STREAM_TIMEOUT_MS = 180_000L;
  private static final String STREAM_ERROR_MESSAGE =
      "Произошла ошибка при обработке запроса. Попробуйте ещё раз.";

  private final ConversationRepository conversationRepository;
  private final MessageRepository messageRepository;
  private final DocumentRetrieval documentRetrieval;
  private final DocumentAccess documentAccess;
  private final CaseAccessProvider caseAccessProvider;
  private final CaseContextProvider caseContextProvider;
  private final DocumentAccessGuard documentAccessGuard;
  private final RagService ragService;
  private final LlmClient llmClient;
  private final DocumentProperties documentProperties;
  private final LegalDomainGuard legalDomainGuard;
  private final LlmQuotaService llmQuotaService;
  private final ThreadPoolTaskExecutor chatStreamExecutor;
  private final Executor chatContextExecutor;
  private final int historyMaxChars;

  public ChatService(
      ConversationRepository conversationRepository,
      MessageRepository messageRepository,
      DocumentRetrieval documentRetrieval,
      DocumentAccess documentAccess,
      CaseAccessProvider caseAccessProvider,
      CaseContextProvider caseContextProvider,
      DocumentAccessGuard documentAccessGuard,
      RagService ragService,
      LlmClient llmClient,
      DocumentProperties documentProperties,
      LegalDomainGuard legalDomainGuard,
      LlmQuotaService llmQuotaService,
      @Qualifier("chatStreamExecutor") ThreadPoolTaskExecutor chatStreamExecutor,
      @Qualifier("chatContextExecutor") Executor chatContextExecutor,
      @Value("${llm.history-max-chars:12000}") int historyMaxChars) {
    this.conversationRepository = conversationRepository;
    this.messageRepository = messageRepository;
    this.documentRetrieval = documentRetrieval;
    this.documentAccess = documentAccess;
    this.caseAccessProvider = caseAccessProvider;
    this.caseContextProvider = caseContextProvider;
    this.documentAccessGuard = documentAccessGuard;
    this.ragService = ragService;
    this.llmClient = llmClient;
    this.documentProperties = documentProperties;
    this.legalDomainGuard = legalDomainGuard;
    this.llmQuotaService = llmQuotaService;
    this.chatStreamExecutor = chatStreamExecutor;
    this.chatContextExecutor = chatContextExecutor;
    this.historyMaxChars = historyMaxChars;
  }

  public ChatResponse chat(ChatRequest request, UUID lawyerId, List<UUID> orgIds) {
    DocumentSummaryView scopedDocument = resolveAccessibleScope(request, lawyerId, orgIds);
    llmQuotaService.assertWithinQuota(lawyerId);
    Conversation conversation =
        resolveConversation(
            request.conversationId(),
            lawyerId,
            request.message(),
            request.caseId(),
            request.documentId());
    boolean isNewConversation = conversation.getId() == null;

    legalDomainGuard.assertLegalQuery(request.message());

    log.info(
        "Chat request received: conversation={}, lawyer={}",
        isNewConversation ? "new" : conversation.getId(),
        lawyerId);

    List<DocumentRef> attachedDocuments =
        loadOwnedAttachedDocuments(request.attachedDocumentIds(), lawyerId);
    PreparedContext context =
        prepareContext(
            request,
            conversation,
            isNewConversation,
            attachedDocuments,
            scopedDocument,
            lawyerId,
            orgIds);

    LlmResult completion =
        llmClient.complete(context.systemPrompt(), context.history(), request.message());
    llmQuotaService.recordUsage(lawyerId, completion.usage().totalTokens());
    FollowUpParser.ParsedAnswer parsed = FollowUpParser.parse(completion.content());
    log.info(
        "LLM chat tokens for lawyer {}: total={}, prompt={}, completion={}",
        lawyerId,
        completion.usage().totalTokens(),
        completion.usage().promptTokens(),
        completion.usage().completionTokens());

    PersistedExchange persisted =
        persistExchange(
            conversation, isNewConversation, request.message(), parsed.answer(), context.sources());

    log.info(
        "Chat response generated for conversation: {} ({} source(s))",
        persisted.conversation().getId(),
        context.sources().size());
    return new ChatResponse(
        persisted.conversation().getId(),
        persisted.assistantMessageId(),
        parsed.answer(),
        context.sources(),
        parsed.followUps());
  }

  public SseEmitter chatStream(ChatRequest request, UUID lawyerId, List<UUID> orgIds) {
    DocumentSummaryView scopedDocument = resolveAccessibleScope(request, lawyerId, orgIds);
    llmQuotaService.assertWithinQuota(lawyerId);
    Conversation conversation =
        resolveConversation(
            request.conversationId(),
            lawyerId,
            request.message(),
            request.caseId(),
            request.documentId());
    boolean isNewConversation = conversation.getId() == null;

    legalDomainGuard.assertLegalQuery(request.message());

    log.info(
        "Chat stream request received: conversation={}, lawyer={}",
        isNewConversation ? "new" : conversation.getId(),
        lawyerId);

    List<DocumentRef> attachedDocuments =
        loadOwnedAttachedDocuments(request.attachedDocumentIds(), lawyerId);

    SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
    Conversation resolved = conversation;
    try {
      chatStreamExecutor.execute(
          () ->
              streamAnswer(
                  emitter,
                  request,
                  lawyerId,
                  orgIds,
                  resolved,
                  isNewConversation,
                  attachedDocuments,
                  scopedDocument));
    } catch (TaskRejectedException e) {
      log.warn("Chat stream rejected: executor saturated (lawyer {})", lawyerId);
      throw new LlmException("Сервис перегружен, попробуйте позже");
    }
    return emitter;
  }

  private void streamAnswer(
      SseEmitter emitter,
      ChatRequest request,
      UUID lawyerId,
      List<UUID> orgIds,
      Conversation conversation,
      boolean isNewConversation,
      List<DocumentRef> attachedDocuments,
      DocumentSummaryView scopedDocument) {
    try {
      PreparedContext context =
          prepareContext(
              request,
              conversation,
              isNewConversation,
              attachedDocuments,
              scopedDocument,
              lawyerId,
              orgIds);

      StreamingAnswerAccumulator accumulator = new StreamingAnswerAccumulator(emitter);
      LlmUsage usage =
          llmClient.streamComplete(
              context.systemPrompt(), context.history(), request.message(), accumulator::onDelta);
      llmQuotaService.recordUsage(lawyerId, usage.totalTokens());

      FollowUpParser.ParsedAnswer parsed = FollowUpParser.parse(accumulator.rawContent());
      PersistedExchange persisted =
          persistExchange(
              conversation,
              isNewConversation,
              request.message(),
              parsed.answer(),
              context.sources());

      log.info(
          "Chat stream completed for conversation {} ({} source(s), {} tokens)",
          persisted.conversation().getId(),
          context.sources().size(),
          usage.totalTokens());
      emitter.send(
          SseEmitter.event()
              .name("done")
              .data(
                  new ChatResponse(
                      persisted.conversation().getId(),
                      persisted.assistantMessageId(),
                      parsed.answer(),
                      context.sources(),
                      parsed.followUps())));
      emitter.complete();
    } catch (StreamAbortedException e) {
      log.info("Chat stream aborted by client for lawyer {}", lawyerId);
      emitter.complete();
    } catch (Exception e) {
      log.error("Chat stream failed for lawyer {}: {}", lawyerId, e.getMessage(), e);
      trySendStreamError(emitter);
    }
  }

  private void trySendStreamError(SseEmitter emitter) {
    try {
      emitter.send(
          SseEmitter.event().name("error").data(new ChatStreamError(STREAM_ERROR_MESSAGE)));
      emitter.complete();
    } catch (IOException io) {
      emitter.completeWithError(io);
    }
  }

  private <T> CompletableFuture<T> supplyContext(Supplier<T> source) {
    return CompletableFuture.supplyAsync(source, chatContextExecutor);
  }

  private List<String> attachedChunkContents(List<DocumentRef> attachedDocuments) {
    if (attachedDocuments.isEmpty()) {
      return List.of();
    }
    return documentAccess.chunkContentsForDocuments(
        attachedDocuments.stream().map(DocumentRef::id).toList());
  }

  private List<LlmMessage> conversationHistory(
      Conversation conversation, boolean isNewConversation) {
    if (isNewConversation) {
      return List.of();
    }
    return buildLlmHistory(
        messageRepository.findTop10ByConversationIdOrderByCreatedAtDesc(conversation.getId()));
  }

  private PreparedContext prepareContext(
      ChatRequest request,
      Conversation conversation,
      boolean isNewConversation,
      List<DocumentRef> attachedDocuments,
      DocumentSummaryView scopedDocument,
      UUID lawyerId,
      List<UUID> orgIds) {
    int topK = documentProperties.topKResults();
    UUID documentId = request.documentId();
    UUID caseId = request.caseId();

    CompletableFuture<List<String>> pendingAttachedChunks =
        supplyContext(() -> attachedChunkContents(attachedDocuments));
    CompletableFuture<List<LlmMessage>> pendingHistory =
        supplyContext(() -> conversationHistory(conversation, isNewConversation));
    CompletableFuture<RetrievedChunks> pendingKnowledgeBase =
        supplyContext(() -> documentRetrieval.retrieveKnowledgeBase(request.message(), topK));
    CompletableFuture<DocumentChunkMatches> pendingDocumentMatches =
        documentId == null
            ? CompletableFuture.completedFuture(DocumentChunkMatches.empty())
            : supplyContext(
                () ->
                    documentRetrieval.retrieveInDocument(
                        List.of(request.message()), topK, documentId));
    CompletableFuture<CaseContext> pendingCaseContext =
        caseId == null
            ? CompletableFuture.completedFuture(null)
            : supplyContext(() -> caseContextProvider.loadContext(caseId, lawyerId, orgIds));
    CompletableFuture<RetrievedChunks> pendingCaseRetrieval =
        caseId == null
            ? CompletableFuture.completedFuture(RetrievedChunks.empty())
            : supplyContext(
                () -> documentRetrieval.retrieveForCase(request.message(), topK, caseId));

    List<String> attachedChunks = Futures.join(pendingAttachedChunks);
    List<LlmMessage> historyForLlm = Futures.join(pendingHistory);
    RetrievedChunks knowledgeBaseRetrieval = Futures.join(pendingKnowledgeBase);
    llmQuotaService.recordTokenUsage(lawyerId, knowledgeBaseRetrieval.llmTokens());
    List<RetrievedChunk> knowledgeBaseMatches = knowledgeBaseRetrieval.chunks();
    boolean legislationPresent =
        knowledgeBaseMatches.stream().anyMatch(RetrievedChunk::legislation);

    if (documentId != null) {
      DocumentChunkMatches matches = Futures.join(pendingDocumentMatches);
      llmQuotaService.recordTokenUsage(lawyerId, matches.totalTokens());
      return buildDocumentContext(
          scopedDocument,
          matches,
          attachedDocuments,
          attachedChunks,
          knowledgeBaseMatches,
          legislationPresent,
          historyForLlm);
    }

    CaseContext caseContext = Futures.join(pendingCaseContext);
    RetrievedChunks caseRetrieval = Futures.join(pendingCaseRetrieval);
    llmQuotaService.recordTokenUsage(lawyerId, caseRetrieval.llmTokens());
    List<RetrievedChunk> caseMatches = caseRetrieval.chunks();

    List<String> relevantChunks = new ArrayList<>(attachedChunks);
    relevantChunks.addAll(caseMatches.stream().map(RetrievedChunk::content).toList());
    relevantChunks.addAll(knowledgeBaseMatches.stream().map(RetrievedChunk::content).toList());

    List<String> sources = new ArrayList<>();
    caseMatches.stream()
        .map(RetrievedChunk::documentTitle)
        .filter(title -> title != null && !title.isBlank())
        .map(title -> CASE_SOURCE_PREFIX + title)
        .forEach(label -> addSource(sources, label));
    knowledgeBaseMatches.stream()
        .map(ChatService::sourceLabel)
        .forEach(label -> addSource(sources, label));
    attachedDocuments.stream().map(DocumentRef::title).forEach(title -> addSource(sources, title));

    if (caseContext == null) {
      return new PreparedContext(
          ragService.buildSystemPrompt(relevantChunks, legislationPresent), sources, historyForLlm);
    }

    return new PreparedContext(
        ragService.buildCaseSystemPrompt(
            caseContext.caseCard(),
            caseContext.hearingTimeline(),
            caseContext.checklist(),
            relevantChunks,
            legislationPresent),
        sources,
        historyForLlm);
  }

  private PreparedContext buildDocumentContext(
      DocumentSummaryView document,
      DocumentChunkMatches matches,
      List<DocumentRef> attachedDocuments,
      List<String> attachedChunks,
      List<RetrievedChunk> knowledgeBaseMatches,
      boolean legislationPresent,
      List<LlmMessage> historyForLlm) {
    List<String> relevantChunks = new ArrayList<>(attachedChunks);
    relevantChunks.addAll(matches.matches().stream().map(DocumentChunkMatch::content).toList());
    relevantChunks.addAll(knowledgeBaseMatches.stream().map(RetrievedChunk::content).toList());

    List<String> sources = new ArrayList<>();
    addSource(sources, DOCUMENT_SOURCE_PREFIX + document.title());
    knowledgeBaseMatches.stream()
        .map(ChatService::sourceLabel)
        .forEach(label -> addSource(sources, label));
    attachedDocuments.stream().map(DocumentRef::title).forEach(title -> addSource(sources, title));

    return new PreparedContext(
        ragService.buildDocumentSystemPrompt(
            document.title(), summaryText(document), relevantChunks, legislationPresent),
        sources,
        historyForLlm);
  }

  private static String summaryText(DocumentSummaryView document) {
    if (document.summaryStatus() != DocumentSummaryStatus.READY) {
      return "";
    }
    StringBuilder text = new StringBuilder();
    if (document.summary() != null && !document.summary().isBlank()) {
      text.append(document.summary().strip());
    }
    for (String keyPoint : document.keyPoints()) {
      text.append(text.isEmpty() ? "" : "\n").append("- ").append(keyPoint);
    }
    return text.toString();
  }

  private static void addSource(List<String> sources, String label) {
    if (label != null && !label.isBlank() && !sources.contains(label)) {
      sources.add(label);
    }
  }

  private void assertCaseAccessible(UUID caseId, UUID lawyerId, List<UUID> orgIds) {
    if (caseId != null) {
      caseAccessProvider.assertCaseVisible(caseId, lawyerId, orgIds);
    }
  }

  private DocumentSummaryView resolveAccessibleScope(
      ChatRequest request, UUID lawyerId, List<UUID> orgIds) {
    if (request.caseId() != null && request.documentId() != null) {
      throw new ChatScopeConflictException();
    }
    assertCaseAccessible(request.caseId(), lawyerId, orgIds);
    if (request.documentId() == null) {
      return null;
    }
    return documentAccessGuard.requireVisible(request.documentId(), lawyerId, orgIds);
  }

  private static String sourceLabel(RetrievedChunk chunk) {
    if (!chunk.legislation()) {
      return chunk.documentTitle();
    }
    StringBuilder label = new StringBuilder();
    if (chunk.articleNumber() != null && !chunk.articleNumber().isBlank()) {
      label.append("ст. ").append(chunk.articleNumber()).append(' ');
    }
    if (chunk.actCanonical() != null && !chunk.actCanonical().isBlank()) {
      label.append(chunk.actCanonical());
    }
    if (chunk.editionDate() != null) {
      label.append(", ред. от ").append(EDITION_DATE_FORMAT.format(chunk.editionDate()));
    }
    String result = label.toString().strip();
    return result.isBlank() ? chunk.documentTitle() : result;
  }

  private PersistedExchange persistExchange(
      Conversation conversation,
      boolean isNewConversation,
      String userMessage,
      String answer,
      List<String> sources) {
    Conversation persisted =
        isNewConversation ? conversationRepository.save(conversation) : conversation;
    messageRepository.save(
        new Message(persisted.getId(), MessageRole.USER, userMessage, List.of()));
    Message assistantMessage =
        messageRepository.save(
            new Message(persisted.getId(), MessageRole.ASSISTANT, answer, sources));
    return new PersistedExchange(persisted, assistantMessage.getId());
  }

  private record PersistedExchange(Conversation conversation, String assistantMessageId) {}

  private record PreparedContext(
      String systemPrompt, List<String> sources, List<LlmMessage> history) {}

  private static final class StreamingAnswerAccumulator {

    private final SseEmitter emitter;
    private final StringBuilder raw = new StringBuilder();
    private int emittedAnswerLength = 0;
    private boolean delimiterReached = false;

    private StreamingAnswerAccumulator(SseEmitter emitter) {
      this.emitter = emitter;
    }

    private void onDelta(String delta) {
      raw.append(delta);
      if (delimiterReached) {
        return;
      }
      int searchFrom = Math.max(0, emittedAnswerLength - FollowUpParser.DELIMITER.length());
      int delimiterIdx = raw.indexOf(FollowUpParser.DELIMITER, searchFrom);
      String answerSoFar = delimiterIdx >= 0 ? raw.substring(0, delimiterIdx) : raw.toString();
      int safeEnd =
          delimiterIdx >= 0
              ? answerSoFar.length()
              : Math.max(0, answerSoFar.length() - FollowUpParser.DELIMITER.length());
      if (safeEnd > emittedAnswerLength) {
        emit(answerSoFar.substring(emittedAnswerLength, safeEnd));
        emittedAnswerLength = safeEnd;
      }
      if (delimiterIdx >= 0) {
        delimiterReached = true;
      }
    }

    private void emit(String content) {
      try {
        emitter.send(SseEmitter.event().name("token").data(new ChatStreamToken(content)));
      } catch (IOException e) {
        throw new StreamAbortedException(e);
      }
    }

    private String rawContent() {
      return raw.toString();
    }
  }

  private static final class StreamAbortedException extends RuntimeException {
    private StreamAbortedException(Throwable cause) {
      super(cause);
    }
  }

  private List<DocumentRef> loadOwnedAttachedDocuments(
      List<UUID> attachedDocumentIds, UUID lawyerId) {
    if (attachedDocumentIds == null || attachedDocumentIds.isEmpty()) {
      return List.of();
    }
    Set<UUID> requestedIds = new HashSet<>(attachedDocumentIds);
    List<DocumentRef> documents = documentAccess.findByIds(requestedIds);
    if (documents.size() != requestedIds.size()) {
      throw new DocumentNotFoundException(firstMissing(requestedIds, documents));
    }

    Set<UUID> caseIds =
        documents.stream()
            .map(DocumentRef::caseId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    Set<UUID> ownedCaseIds =
        caseIds.isEmpty() ? Set.of() : caseAccessProvider.retainCasesOwnedBy(caseIds, lawyerId);

    for (DocumentRef document : documents) {
      boolean owned =
          document.caseId() == null
              ? lawyerId.equals(document.uploadedBy())
              : ownedCaseIds.contains(document.caseId());
      if (!owned) {
        log.warn(
            "Lawyer {} attempted to attach document {} owned by another user",
            lawyerId,
            document.id());
        throw new DocumentNotFoundException(document.id());
      }
    }

    return documents;
  }

  private UUID firstMissing(Set<UUID> requestedIds, List<DocumentRef> documents) {
    Set<UUID> foundIds = documents.stream().map(DocumentRef::id).collect(Collectors.toSet());
    return requestedIds.stream().filter(id -> !foundIds.contains(id)).findFirst().orElseThrow();
  }

  public Page<ConversationResponse> getConversations(
      UUID lawyerId,
      String query,
      UUID caseId,
      UUID documentId,
      List<UUID> orgIds,
      int page,
      int size) {
    if (caseId != null && documentId != null) {
      throw new ChatScopeConflictException();
    }
    assertCaseAccessible(caseId, lawyerId, orgIds);
    if (documentId != null) {
      documentAccessGuard.requireVisible(documentId, lawyerId, orgIds);
    }
    var pageRequest = PageRequests.of(page, size);
    boolean filtered = query != null && !query.isBlank();
    String title = filtered ? query.trim() : null;
    Page<Conversation> conversations;
    if (documentId != null) {
      conversations =
          filtered
              ? conversationRepository
                  .findByLawyerIdAndDocumentIdAndTitleContainingIgnoreCaseOrderByCreatedAtDesc(
                      lawyerId, documentId, title, pageRequest)
              : conversationRepository.findByLawyerIdAndDocumentIdOrderByCreatedAtDesc(
                  lawyerId, documentId, pageRequest);
    } else if (caseId == null) {
      conversations =
          filtered
              ? conversationRepository
                  .findByLawyerIdAndCaseIdIsNullAndDocumentIdIsNullAndTitleContainingIgnoreCaseOrderByCreatedAtDesc(
                      lawyerId, title, pageRequest)
              : conversationRepository
                  .findByLawyerIdAndCaseIdIsNullAndDocumentIdIsNullOrderByCreatedAtDesc(
                      lawyerId, pageRequest);
    } else {
      conversations =
          filtered
              ? conversationRepository
                  .findByLawyerIdAndCaseIdAndTitleContainingIgnoreCaseOrderByCreatedAtDesc(
                      lawyerId, caseId, title, pageRequest)
              : conversationRepository.findByLawyerIdAndCaseIdOrderByCreatedAtDesc(
                  lawyerId, caseId, pageRequest);
    }
    return conversations.map(
        c -> new ConversationResponse(c.getId(), c.getTitle(), c.getCreatedAt()));
  }

  public MessageResponse rateMessage(String messageId, RateRequest request, UUID lawyerId) {
    Message message =
        messageRepository
            .findById(messageId)
            .orElseThrow(() -> new MessageNotFoundException(messageId));
    Conversation conversation =
        conversationRepository
            .findById(message.getConversationId())
            .orElseThrow(() -> new MessageNotFoundException(messageId));
    if (!conversation.getLawyerId().equals(lawyerId)) {
      log.warn("Lawyer {} attempted to rate message {} owned by another user", lawyerId, messageId);
      throw new MessageNotFoundException(messageId);
    }

    message.setRating(request.rating());
    message.setRatingComment(request.comment());
    Message saved = messageRepository.save(message);
    log.info("Message {} rated {} by lawyer {}", messageId, request.rating(), lawyerId);
    return MessageResponse.from(saved);
  }

  public Page<MessageResponse> getMessages(
      String conversationId, UUID lawyerId, int page, int size) {
    Conversation conversation =
        conversationRepository
            .findById(conversationId)
            .orElseThrow(() -> new ConversationNotFoundException(conversationId));

    if (!conversation.getLawyerId().equals(lawyerId)) {
      log.warn(
          "Lawyer {} attempted to access conversation {} owned by another user",
          lawyerId,
          conversationId);
      throw new ConversationNotFoundException(conversationId);
    }

    Page<Message> messages =
        messageRepository.findByConversationIdOrderByCreatedAtDesc(
            conversationId, PageRequests.of(page, size));
    List<MessageResponse> chronological =
        new ArrayList<>(messages.getContent().stream().map(MessageResponse::from).toList());
    Collections.reverse(chronological);
    return new PageImpl<>(chronological, messages.getPageable(), messages.getTotalElements());
  }

  private Conversation resolveConversation(
      String conversationId, UUID lawyerId, String firstMessage, UUID caseId, UUID documentId) {
    if (conversationId != null) {
      Conversation existing =
          conversationRepository
              .findById(conversationId)
              .filter(c -> c.getLawyerId().equals(lawyerId))
              .orElseThrow(() -> new ConversationNotFoundException(conversationId));
      if (!Objects.equals(existing.getCaseId(), caseId)) {
        log.warn(
            "Lawyer {} sent message with case {} into conversation {} bound to case {}",
            lawyerId,
            caseId,
            conversationId,
            existing.getCaseId());
        throw new ConversationCaseMismatchException(conversationId);
      }
      if (!Objects.equals(existing.getDocumentId(), documentId)) {
        log.warn(
            "Lawyer {} sent message with document {} into conversation {} bound to document {}",
            lawyerId,
            documentId,
            conversationId,
            existing.getDocumentId());
        throw new ConversationDocumentMismatchException(conversationId);
      }
      return existing;
    }
    String title =
        firstMessage.length() > TITLE_MAX_LENGTH
            ? firstMessage.substring(0, TITLE_MAX_LENGTH) + "..."
            : firstMessage;
    return new Conversation(lawyerId, title, caseId, documentId);
  }

  private List<LlmMessage> buildLlmHistory(List<Message> recentDescending) {
    List<Message> withinBudget = applyCharBudget(recentDescending);
    List<Message> chronological = new ArrayList<>(withinBudget);
    Collections.reverse(chronological);

    return chronological.stream()
        .dropWhile(m -> m.getRole() != MessageRole.USER)
        .map(m -> new LlmMessage(m.getRole().name().toLowerCase(), m.getContent()))
        .toList();
  }

  private List<Message> applyCharBudget(List<Message> recentDescending) {
    List<Message> kept = new ArrayList<>();
    int used = 0;
    for (Message message : recentDescending) {
      int cost = message.getContent() == null ? 0 : message.getContent().length();
      if (!kept.isEmpty() && used + cost > historyMaxChars) {
        break;
      }
      kept.add(message);
      used += cost;
    }
    return kept;
  }
}
