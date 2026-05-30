package com.pravoos.ai.controller;

import com.pravoos.ai.model.dto.ChatRequest;
import com.pravoos.ai.model.dto.ChatResponse;
import com.pravoos.ai.model.dto.ConversationResponse;
import com.pravoos.ai.model.mongo.Message;
import com.pravoos.ai.service.ChatService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/ai")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(
            @Valid @RequestBody ChatRequest request,
            Authentication authentication) {
        UUID lawyerId = UUID.fromString((String) authentication.getPrincipal());
        return ResponseEntity.ok(chatService.chat(request, lawyerId));
    }

    @GetMapping("/conversations")
    public ResponseEntity<List<ConversationResponse>> getConversations(Authentication authentication) {
        UUID lawyerId = UUID.fromString((String) authentication.getPrincipal());
        return ResponseEntity.ok(chatService.getConversations(lawyerId));
    }

    @GetMapping("/conversations/{id}/messages")
    public ResponseEntity<List<Message>> getMessages(
            @PathVariable String id,
            Authentication authentication) {
        UUID lawyerId = UUID.fromString((String) authentication.getPrincipal());
        return ResponseEntity.ok(chatService.getMessages(id, lawyerId));
    }
}
