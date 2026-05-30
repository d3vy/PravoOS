package com.pravoos.ai.model.mongo;

import com.pravoos.ai.model.enums.MessageRole;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Document(collection = "messages")
public class Message {

    @Id
    private String id;

    @Indexed
    private String conversationId;

    private MessageRole role;

    private String content;

    private List<String> sources;

    private LocalDateTime createdAt;

    public Message() {}

    public Message(String conversationId, MessageRole role, String content, List<String> sources) {
        this.conversationId = conversationId;
        this.role = role;
        this.content = content;
        this.sources = sources;
        this.createdAt = LocalDateTime.now();
    }

    public String getId() { return id; }

    public String getConversationId() { return conversationId; }

    public MessageRole getRole() { return role; }

    public String getContent() { return content; }

    public List<String> getSources() { return sources; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}
