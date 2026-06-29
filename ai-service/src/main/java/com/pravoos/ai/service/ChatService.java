package com.pravoos.ai.service;

import com.pravoos.ai.config.DocumentProperties;
import com.pravoos.ai.exception.ConversationNotFoundException;
import com.pravoos.ai.exception.DocumentNotFoundException;
import com.pravoos.ai.exception.MessageNotFoundException;
import com.pravoos.ai.llm.LlmClient;
import com.pravoos.ai.llm.LlmResult;
import com.pravoos.ai.llm.dto.LlmMessage;
import com.pravoos.ai.model.dto.*;
import com.pravoos.ai.model.entity.Case;
import com.pravoos.ai.model.entity.Document;
import com.pravoos.ai.model.enums.MessageRole;
import com.pravoos.ai.model.mongo.Conversation;
import com.pravoos.ai.model.mongo.Message;
import com.pravoos.ai.repository.ChunkMatch;
import com.pravoos.ai.repository.VectorSearchRepository;
import com.pravoos.ai.repository.jpa.CaseRepository;
import com.pravoos.ai.repository.jpa.DocumentChunkRepository;
import com.pravoos.ai.repository.jpa.DocumentRepository;
import com.pravoos.ai.repository.mongo.ConversationRepository;
import com.pravoos.ai.repository.mongo.MessageRepository;
import com.pravoos.ai.util.PageRequests;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    private static final int TITLE_MAX_LENGTH = 60;

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final VectorSearchRepository vectorSearchRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final DocumentRepository documentRepository;
    private final CaseRepository caseRepository;
    private final EmbeddingService embeddingService;
    private final RagService ragService;
    private final LlmClient llmClient;
    private final DocumentProperties documentProperties;
    private final LegalDomainGuard legalDomainGuard;
    private final LlmQuotaService llmQuotaService;
    private final int historyMaxChars;

    public ChatService(ConversationRepository conversationRepository,
                       MessageRepository messageRepository,
                       VectorSearchRepository vectorSearchRepository,
                       DocumentChunkRepository documentChunkRepository,
                       DocumentRepository documentRepository,
                       CaseRepository caseRepository,
                       EmbeddingService embeddingService,
                       RagService ragService,
                       LlmClient llmClient,
                       DocumentProperties documentProperties,
                       LegalDomainGuard legalDomainGuard,
                       LlmQuotaService llmQuotaService,
                       @Value("${llm.history-max-chars:12000}") int historyMaxChars) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.vectorSearchRepository = vectorSearchRepository;
        this.documentChunkRepository = documentChunkRepository;
        this.documentRepository = documentRepository;
        this.caseRepository = caseRepository;
        this.embeddingService = embeddingService;
        this.ragService = ragService;
        this.llmClient = llmClient;
        this.documentProperties = documentProperties;
        this.legalDomainGuard = legalDomainGuard;
        this.llmQuotaService = llmQuotaService;
        this.historyMaxChars = historyMaxChars;
    }

    public ChatResponse chat(ChatRequest request, UUID lawyerId) {
        llmQuotaService.assertWithinQuota(lawyerId);
        Conversation conversation = resolveConversation(request.conversationId(), lawyerId, request.message());
        boolean isNewConversation = conversation.getId() == null;

        if (isNewConversation) {
            legalDomainGuard.assertLegalQuery(request.message());
        }

        log.info("Chat request received: conversation={}, lawyer={}",
                isNewConversation ? "new" : conversation.getId(), lawyerId);

        List<Document> attachedDocuments = loadOwnedAttachedDocuments(request.attachedDocumentIds(), lawyerId);
        List<String> attachedChunks = attachedDocuments.isEmpty()
                ? List.of()
                : documentChunkRepository.findContentByDocumentIdIn(
                        attachedDocuments.stream().map(Document::getId).collect(Collectors.toSet()));

        List<LlmMessage> historyForLlm = isNewConversation
                ? List.of()
                : buildLlmHistory(messageRepository.findTop10ByConversationIdOrderByCreatedAtDesc(conversation.getId()));

        float[] queryEmbedding = embeddingService.embed(request.message());
        List<ChunkMatch> matches = vectorSearchRepository
                .findTopKInKnowledgeBase(queryEmbedding, documentProperties.topKResults());

        List<String> relevantChunks = new ArrayList<>(matches.stream().map(ChunkMatch::content).toList());
        List<String> sources = new ArrayList<>(matches.stream()
                .map(ChunkMatch::documentTitle)
                .filter(title -> title != null && !title.isBlank())
                .distinct()
                .toList());
        relevantChunks.addAll(0, attachedChunks);

        attachedDocuments.stream()
                .map(Document::getTitle)
                .filter(title -> title != null && !title.isBlank())
                .filter(title -> !sources.contains(title))
                .forEach(sources::add);

        String systemPrompt = ragService.buildSystemPrompt(relevantChunks);
        LlmResult completion = llmClient.complete(systemPrompt, historyForLlm, request.message());
        FollowUpParser.ParsedAnswer parsed = FollowUpParser.parse(completion.content());
        log.info("LLM chat tokens for lawyer {}: total={}, prompt={}, completion={}",
                lawyerId, completion.usage().totalTokens(),
                completion.usage().promptTokens(), completion.usage().completionTokens());

        if (isNewConversation) {
            conversation = conversationRepository.save(conversation);
        }
        messageRepository.save(new Message(conversation.getId(), MessageRole.USER, request.message(), List.of()));
        messageRepository.save(new Message(conversation.getId(), MessageRole.ASSISTANT, parsed.answer(), sources));

        log.info("Chat response generated for conversation: {} ({} source(s))", conversation.getId(), sources.size());
        return new ChatResponse(conversation.getId(), parsed.answer(), sources, parsed.followUps());
    }

    private List<Document> loadOwnedAttachedDocuments(List<UUID> attachedDocumentIds, UUID lawyerId) {
        if (attachedDocumentIds == null || attachedDocumentIds.isEmpty()) {
            return List.of();
        }
        Set<UUID> requestedIds = new HashSet<>(attachedDocumentIds);
        List<Document> documents = documentRepository.findAllById(requestedIds);
        if (documents.size() != requestedIds.size()) {
            throw new DocumentNotFoundException(firstMissing(requestedIds, documents));
        }

        Set<UUID> caseIds = documents.stream()
                .map(Document::getCaseId)
                .collect(Collectors.toSet());
        if (caseIds.contains(null)) {
            UUID offending = documents.stream()
                    .filter(document -> document.getCaseId() == null)
                    .map(Document::getId)
                    .findFirst()
                    .orElseThrow();
            log.warn("Lawyer {} attempted to attach non-case document {}", lawyerId, offending);
            throw new DocumentNotFoundException(offending);
        }

        Map<UUID, Case> casesById = caseRepository.findAllById(caseIds).stream()
                .collect(Collectors.toMap(Case::getId, Function.identity()));
        for (Document document : documents) {
            Case ownerCase = casesById.get(document.getCaseId());
            if (ownerCase == null || !ownerCase.getLawyerId().equals(lawyerId)) {
                log.warn("Lawyer {} attempted to attach document {} owned by another user", lawyerId, document.getId());
                throw new DocumentNotFoundException(document.getId());
            }
        }

        return documents;
    }

    private UUID firstMissing(Set<UUID> requestedIds, List<Document> documents) {
        Set<UUID> foundIds = documents.stream().map(Document::getId).collect(Collectors.toSet());
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
