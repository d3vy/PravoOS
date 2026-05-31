package com.pravoos.ai.service;

import com.pravoos.ai.config.DocumentProperties;
import com.pravoos.ai.exception.ConversationNotFoundException;
import com.pravoos.ai.exception.MessageNotFoundException;
import com.pravoos.ai.model.dto.RateRequest;
import com.pravoos.ai.llm.LlmClient;
import com.pravoos.ai.llm.dto.LlmMessage;
import com.pravoos.ai.model.dto.ChatRequest;
import com.pravoos.ai.model.dto.ChatResponse;
import com.pravoos.ai.model.dto.ConversationResponse;
import com.pravoos.ai.model.dto.MessageResponse;
import com.pravoos.ai.model.enums.MessageRole;
import com.pravoos.ai.model.mongo.Conversation;
import com.pravoos.ai.model.mongo.Message;
import com.pravoos.ai.repository.ChunkMatch;
import com.pravoos.ai.repository.mongo.ConversationRepository;
import com.pravoos.ai.repository.mongo.MessageRepository;
import com.pravoos.ai.repository.VectorSearchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    private static final int TITLE_MAX_LENGTH = 60;

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final VectorSearchRepository vectorSearchRepository;
    private final EmbeddingService embeddingService;
    private final RagService ragService;
    private final LlmClient llmClient;
    private final DocumentProperties documentProperties;

    public ChatService(ConversationRepository conversationRepository,
                       MessageRepository messageRepository,
                       VectorSearchRepository vectorSearchRepository,
                       EmbeddingService embeddingService,
                       RagService ragService,
                       LlmClient llmClient,
                       DocumentProperties documentProperties) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.vectorSearchRepository = vectorSearchRepository;
        this.embeddingService = embeddingService;
        this.ragService = ragService;
        this.llmClient = llmClient;
        this.documentProperties = documentProperties;
    }

    public ChatResponse chat(ChatRequest request, UUID lawyerId) {
        Conversation conversation = resolveConversation(request.conversationId(), lawyerId, request.message());
        boolean isNewConversation = conversation.getId() == null;
        log.info("Chat request received: conversation={}, lawyer={}",
                isNewConversation ? "new" : conversation.getId(), lawyerId);

        List<LlmMessage> historyForLlm = isNewConversation
                ? List.of()
                : buildLlmHistory(messageRepository.findTop10ByConversationIdOrderByCreatedAtDesc(conversation.getId()));

        float[] queryEmbedding = embeddingService.embed(request.message());
        List<ChunkMatch> matches = vectorSearchRepository
                .findTopKInKnowledgeBase(queryEmbedding, documentProperties.topKResults());

        List<String> relevantChunks = matches.stream().map(ChunkMatch::content).toList();
        List<String> sources = matches.stream()
                .map(ChunkMatch::documentTitle)
                .filter(title -> title != null && !title.isBlank())
                .distinct()
                .toList();

        String systemPrompt = ragService.buildSystemPrompt(relevantChunks);
        String answer = llmClient.complete(systemPrompt, historyForLlm, request.message());

        if (isNewConversation) {
            conversation = conversationRepository.save(conversation);
        }
        messageRepository.save(new Message(conversation.getId(), MessageRole.USER, request.message(), List.of()));
        messageRepository.save(new Message(conversation.getId(), MessageRole.ASSISTANT, answer, sources));

        log.info("Chat response generated for conversation: {} ({} source(s))", conversation.getId(), sources.size());
        return new ChatResponse(conversation.getId(), answer, sources);
    }

    public List<ConversationResponse> getConversations(UUID lawyerId) {
        return conversationRepository.findTop100ByLawyerIdOrderByCreatedAtDesc(lawyerId)
                .stream()
                .map(c -> new ConversationResponse(c.getId(), c.getTitle(), c.getCreatedAt()))
                .toList();
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

    public List<MessageResponse> getMessages(String conversationId, UUID lawyerId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ConversationNotFoundException(conversationId));

        if (!conversation.getLawyerId().equals(lawyerId)) {
            log.warn("Lawyer {} attempted to access conversation {} owned by another user",
                    lawyerId, conversationId);
            throw new ConversationNotFoundException(conversationId);
        }

        return messageRepository.findByConversationIdOrderByCreatedAt(conversationId)
                .stream()
                .map(MessageResponse::from)
                .toList();
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
        List<Message> chronological = new ArrayList<>(recentDescending);
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
}
