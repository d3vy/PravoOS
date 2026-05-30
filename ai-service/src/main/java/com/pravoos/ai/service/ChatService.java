package com.pravoos.ai.service;

import com.pravoos.ai.config.DocumentProperties;
import com.pravoos.ai.exception.ConversationNotFoundException;
import com.pravoos.ai.llm.LlmClient;
import com.pravoos.ai.llm.dto.LlmMessage;
import com.pravoos.ai.model.dto.ChatRequest;
import com.pravoos.ai.model.dto.ChatResponse;
import com.pravoos.ai.model.dto.ConversationResponse;
import com.pravoos.ai.model.enums.MessageRole;
import com.pravoos.ai.model.mongo.Conversation;
import com.pravoos.ai.model.mongo.Message;
import com.pravoos.ai.repository.ConversationRepository;
import com.pravoos.ai.repository.MessageRepository;
import com.pravoos.ai.repository.VectorSearchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    private static final int MAX_HISTORY_MESSAGES = 10;
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

        List<Message> history = messageRepository.findByConversationIdOrderByCreatedAt(conversation.getId());
        List<LlmMessage> historyForLlm = buildLlmHistory(history);

        float[] queryEmbedding = embeddingService.embed(request.message());
        List<String> relevantChunks = vectorSearchRepository
                .findTopKContentBySimilarity(queryEmbedding, documentProperties.topKResults());

        String systemPrompt = ragService.buildSystemPrompt(relevantChunks);
        String answer = llmClient.complete(systemPrompt, historyForLlm, request.message());

        messageRepository.save(new Message(conversation.getId(), MessageRole.USER, request.message(), List.of()));
        messageRepository.save(new Message(conversation.getId(), MessageRole.ASSISTANT, answer, List.of()));

        log.info("Chat response generated for conversation: {}", conversation.getId());
        return new ChatResponse(conversation.getId(), answer, List.of());
    }

    public List<ConversationResponse> getConversations(UUID lawyerId) {
        return conversationRepository.findByLawyerIdOrderByCreatedAtDesc(lawyerId)
                .stream()
                .map(c -> new ConversationResponse(c.getId(), c.getTitle(), c.getCreatedAt()))
                .toList();
    }

    public List<Message> getMessages(String conversationId, UUID lawyerId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ConversationNotFoundException(conversationId));

        if (!conversation.getLawyerId().equals(lawyerId)) {
            throw new ConversationNotFoundException(conversationId);
        }

        return messageRepository.findByConversationIdOrderByCreatedAt(conversationId);
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
        return conversationRepository.save(new Conversation(lawyerId, title));
    }

    private List<LlmMessage> buildLlmHistory(List<Message> messages) {
        int from = Math.max(0, messages.size() - MAX_HISTORY_MESSAGES);
        return messages.subList(from, messages.size())
                .stream()
                .map(m -> new LlmMessage(m.getRole().name().toLowerCase(), m.getContent()))
                .toList();
    }
}
