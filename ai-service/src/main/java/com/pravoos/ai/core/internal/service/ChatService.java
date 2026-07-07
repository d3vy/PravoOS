package com.pravoos.ai.core.internal.service;

import com.pravoos.ai.core.api.CaseAccessProvider;
import com.pravoos.ai.core.internal.dto.*;
import com.pravoos.ai.core.internal.model.mongo.Conversation;
import com.pravoos.ai.core.internal.model.mongo.Message;
import com.pravoos.ai.core.internal.repository.mongo.ConversationRepository;
import com.pravoos.ai.core.internal.repository.mongo.MessageRepository;
import com.pravoos.ai.document.api.DocumentAccess;
import com.pravoos.ai.document.api.DocumentRef;
import com.pravoos.ai.document.api.DocumentRetrieval;
import com.pravoos.ai.document.api.RetrievedChunk;
import com.pravoos.ai.llm.api.LlmClient;
import com.pravoos.ai.llm.api.LlmMessage;
import com.pravoos.ai.llm.api.LlmResult;
import com.pravoos.ai.llm.api.LlmUsage;
import com.pravoos.ai.shared.config.DocumentProperties;
import com.pravoos.ai.shared.exception.ConversationNotFoundException;
import com.pravoos.ai.shared.exception.DocumentNotFoundException;
import com.pravoos.ai.shared.exception.LlmException;
import com.pravoos.ai.shared.exception.MessageNotFoundException;
import com.pravoos.ai.shared.model.enums.MessageRole;
import com.pravoos.ai.shared.service.LlmQuotaService;
import com.pravoos.ai.shared.util.PageRequests;
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

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    private static final int TITLE_MAX_LENGTH = 60;
    private static final long STREAM_TIMEOUT_MS = 180_000L;
    private static final String STREAM_ERROR_MESSAGE =
            "Произошла ошибка при обработке запроса. Попробуйте ещё раз.";

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final DocumentRetrieval documentRetrieval;
    private final DocumentAccess documentAccess;
    private final CaseAccessProvider caseAccessProvider;
    private final RagService ragService;
    private final LlmClient llmClient;
    private final DocumentProperties documentProperties;
    private final LegalDomainGuard legalDomainGuard;
    private final LlmQuotaService llmQuotaService;
    private final ThreadPoolTaskExecutor chatStreamExecutor;
    private final int historyMaxChars;

    public ChatService(ConversationRepository conversationRepository,
                       MessageRepository messageRepository,
                       DocumentRetrieval documentRetrieval,
                       DocumentAccess documentAccess,
                       CaseAccessProvider caseAccessProvider,
                       RagService ragService,
                       LlmClient llmClient,
                       DocumentProperties documentProperties,
                       LegalDomainGuard legalDomainGuard,
                       LlmQuotaService llmQuotaService,
                       @Qualifier("chatStreamExecutor") ThreadPoolTaskExecutor chatStreamExecutor,
                       @Value("${llm.history-max-chars:12000}") int historyMaxChars) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.documentRetrieval = documentRetrieval;
        this.documentAccess = documentAccess;
        this.caseAccessProvider = caseAccessProvider;
        this.ragService = ragService;
        this.llmClient = llmClient;
        this.documentProperties = documentProperties;
        this.legalDomainGuard = legalDomainGuard;
        this.llmQuotaService = llmQuotaService;
        this.chatStreamExecutor = chatStreamExecutor;
        this.historyMaxChars = historyMaxChars;
    }

    public ChatResponse chat(ChatRequest request, UUID lawyerId) {
        llmQuotaService.assertWithinQuota(lawyerId);
        Conversation conversation = resolveConversation(request.conversationId(), lawyerId, request.message());
        boolean isNewConversation = conversation.getId() == null;

        legalDomainGuard.assertLegalQuery(request.message());

        log.info("Chat request received: conversation={}, lawyer={}",
                isNewConversation ? "new" : conversation.getId(), lawyerId);

        List<DocumentRef> attachedDocuments = loadOwnedAttachedDocuments(request.attachedDocumentIds(), lawyerId);
        PreparedContext context = prepareContext(request, conversation, isNewConversation, attachedDocuments);

        LlmResult completion = llmClient.complete(context.systemPrompt(), context.history(), request.message());
        llmQuotaService.recordUsage(lawyerId, completion.usage().totalTokens());
        FollowUpParser.ParsedAnswer parsed = FollowUpParser.parse(completion.content());
        log.info("LLM chat tokens for lawyer {}: total={}, prompt={}, completion={}",
                lawyerId, completion.usage().totalTokens(),
                completion.usage().promptTokens(), completion.usage().completionTokens());

        Conversation persisted = persistExchange(conversation, isNewConversation, request.message(),
                parsed.answer(), context.sources());

        log.info("Chat response generated for conversation: {} ({} source(s))",
                persisted.getId(), context.sources().size());
        return new ChatResponse(persisted.getId(), parsed.answer(), context.sources(), parsed.followUps());
    }

    public SseEmitter chatStream(ChatRequest request, UUID lawyerId) {
        llmQuotaService.assertWithinQuota(lawyerId);
        Conversation conversation = resolveConversation(request.conversationId(), lawyerId, request.message());
        boolean isNewConversation = conversation.getId() == null;

        legalDomainGuard.assertLegalQuery(request.message());

        log.info("Chat stream request received: conversation={}, lawyer={}",
                isNewConversation ? "new" : conversation.getId(), lawyerId);

        List<DocumentRef> attachedDocuments = loadOwnedAttachedDocuments(request.attachedDocumentIds(), lawyerId);

        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        Conversation resolved = conversation;
        try {
            chatStreamExecutor.execute(() ->
                    streamAnswer(emitter, request, lawyerId, resolved, isNewConversation, attachedDocuments));
        } catch (TaskRejectedException e) {
            log.warn("Chat stream rejected: executor saturated (lawyer {})", lawyerId);
            throw new LlmException("Сервис перегружен, попробуйте позже");
        }
        return emitter;
    }

    private void streamAnswer(SseEmitter emitter, ChatRequest request, UUID lawyerId,
                              Conversation conversation, boolean isNewConversation,
                              List<DocumentRef> attachedDocuments) {
        try {
            PreparedContext context = prepareContext(request, conversation, isNewConversation, attachedDocuments);

            StreamingAnswerAccumulator accumulator = new StreamingAnswerAccumulator(emitter);
            LlmUsage usage = llmClient.streamComplete(
                    context.systemPrompt(), context.history(), request.message(), accumulator::onDelta);
            llmQuotaService.recordUsage(lawyerId, usage.totalTokens());

            FollowUpParser.ParsedAnswer parsed = FollowUpParser.parse(accumulator.rawContent());
            Conversation persisted = persistExchange(conversation, isNewConversation, request.message(),
                    parsed.answer(), context.sources());

            log.info("Chat stream completed for conversation {} ({} source(s), {} tokens)",
                    persisted.getId(), context.sources().size(), usage.totalTokens());
            emitter.send(SseEmitter.event().name("done")
                    .data(new ChatResponse(persisted.getId(), parsed.answer(), context.sources(), parsed.followUps())));
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
            emitter.send(SseEmitter.event().name("error").data(new ChatStreamError(STREAM_ERROR_MESSAGE)));
            emitter.complete();
        } catch (IOException io) {
            emitter.completeWithError(io);
        }
    }

    private PreparedContext prepareContext(ChatRequest request, Conversation conversation,
                                           boolean isNewConversation, List<DocumentRef> attachedDocuments) {
        List<String> attachedChunks = attachedDocuments.isEmpty()
                ? List.of()
                : documentAccess.chunkContentsForDocuments(
                        attachedDocuments.stream().map(DocumentRef::id).toList());

        List<LlmMessage> historyForLlm = isNewConversation
                ? List.of()
                : buildLlmHistory(messageRepository.findTop10ByConversationIdOrderByCreatedAtDesc(conversation.getId()));

        List<RetrievedChunk> matches = documentRetrieval
                .retrieveKnowledgeBase(request.message(), documentProperties.topKResults());

        List<String> relevantChunks = new ArrayList<>(matches.stream().map(RetrievedChunk::content).toList());
        List<String> sources = new ArrayList<>(matches.stream()
                .map(RetrievedChunk::documentTitle)
                .filter(title -> title != null && !title.isBlank())
                .distinct()
                .toList());
        relevantChunks.addAll(0, attachedChunks);

        attachedDocuments.stream()
                .map(DocumentRef::title)
                .filter(title -> title != null && !title.isBlank())
                .filter(title -> !sources.contains(title))
                .forEach(sources::add);

        return new PreparedContext(ragService.buildSystemPrompt(relevantChunks), sources, historyForLlm);
    }

    private Conversation persistExchange(Conversation conversation, boolean isNewConversation,
                                         String userMessage, String answer, List<String> sources) {
        Conversation persisted = isNewConversation ? conversationRepository.save(conversation) : conversation;
        messageRepository.save(new Message(persisted.getId(), MessageRole.USER, userMessage, List.of()));
        messageRepository.save(new Message(persisted.getId(), MessageRole.ASSISTANT, answer, sources));
        return persisted;
    }

    private record PreparedContext(String systemPrompt, List<String> sources, List<LlmMessage> history) {}

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
            int delimiterIdx = raw.indexOf(FollowUpParser.DELIMITER);
            String answerSoFar = delimiterIdx >= 0 ? raw.substring(0, delimiterIdx) : raw.toString();
            int safeEnd = delimiterIdx >= 0
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

    private List<DocumentRef> loadOwnedAttachedDocuments(List<UUID> attachedDocumentIds, UUID lawyerId) {
        if (attachedDocumentIds == null || attachedDocumentIds.isEmpty()) {
            return List.of();
        }
        Set<UUID> requestedIds = new HashSet<>(attachedDocumentIds);
        List<DocumentRef> documents = documentAccess.findByIds(requestedIds);
        if (documents.size() != requestedIds.size()) {
            throw new DocumentNotFoundException(firstMissing(requestedIds, documents));
        }

        Set<UUID> caseIds = documents.stream()
                .map(DocumentRef::caseId)
                .collect(Collectors.toSet());
        if (caseIds.contains(null)) {
            UUID offending = documents.stream()
                    .filter(document -> document.caseId() == null)
                    .map(DocumentRef::id)
                    .findFirst()
                    .orElseThrow();
            log.warn("Lawyer {} attempted to attach non-case document {}", lawyerId, offending);
            throw new DocumentNotFoundException(offending);
        }

        Set<UUID> ownedCaseIds = caseAccessProvider.retainCasesOwnedBy(caseIds, lawyerId);
        for (DocumentRef document : documents) {
            if (!ownedCaseIds.contains(document.caseId())) {
                log.warn("Lawyer {} attempted to attach document {} owned by another user", lawyerId, document.id());
                throw new DocumentNotFoundException(document.id());
            }
        }

        return documents;
    }

    private UUID firstMissing(Set<UUID> requestedIds, List<DocumentRef> documents) {
        Set<UUID> foundIds = documents.stream().map(DocumentRef::id).collect(Collectors.toSet());
        return requestedIds.stream()
                .filter(id -> !foundIds.contains(id))
                .findFirst()
                .orElseThrow();
    }

    public Page<ConversationResponse> getConversations(UUID lawyerId, String query, int page, int size) {
        var pageRequest = PageRequests.of(page, size);
        Page<Conversation> conversations = (query != null && !query.isBlank())
                ? conversationRepository.findByLawyerIdAndTitleContainingIgnoreCaseOrderByCreatedAtDesc(
                        lawyerId, query.trim(), pageRequest)
                : conversationRepository.findByLawyerIdOrderByCreatedAtDesc(lawyerId, pageRequest);
        return conversations.map(c -> new ConversationResponse(c.getId(), c.getTitle(), c.getCreatedAt()));
    }

    public MessageResponse rateMessage(String messageId, RateRequest request, UUID lawyerId) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new MessageNotFoundException(messageId));
        Conversation conversation = conversationRepository.findById(message.getConversationId())
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

    public Page<MessageResponse> getMessages(String conversationId, UUID lawyerId, int page, int size) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ConversationNotFoundException(conversationId));

        if (!conversation.getLawyerId().equals(lawyerId)) {
            log.warn("Lawyer {} attempted to access conversation {} owned by another user",
                    lawyerId, conversationId);
            throw new ConversationNotFoundException(conversationId);
        }

        Page<Message> messages = messageRepository
                .findByConversationIdOrderByCreatedAtDesc(conversationId, PageRequests.of(page, size));
        List<MessageResponse> chronological = new ArrayList<>(
                messages.getContent().stream().map(MessageResponse::from).toList());
        Collections.reverse(chronological);
        return new PageImpl<>(chronological, messages.getPageable(), messages.getTotalElements());
    }

    private Conversation resolveConversation(String conversationId, UUID lawyerId, String firstMessage) {
        if (conversationId != null) {
            return conversationRepository.findById(conversationId)
                    .filter(c -> c.getLawyerId().equals(lawyerId))
                    .orElseThrow(() -> new ConversationNotFoundException(conversationId));
        }
        String title = firstMessage.length() > TITLE_MAX_LENGTH
                ? firstMessage.substring(0, TITLE_MAX_LENGTH) + "..."
                : firstMessage;
        return new Conversation(lawyerId, title);
    }

    private List<LlmMessage> buildLlmHistory(List<Message> recentDescending) {
        List<Message> withinBudget = applyCharBudget(recentDescending);
        List<Message> chronological = new ArrayList<>(withinBudget);
        Collections.reverse(chronological);

        int firstUser = 0;
        while (firstUser < chronological.size() && chronological.get(firstUser).getRole() != MessageRole.USER) {
            firstUser++;
        }

        return chronological.subList(firstUser, chronological.size())
                .stream()
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
