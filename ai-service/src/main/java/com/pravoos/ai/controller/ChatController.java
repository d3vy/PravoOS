package com.pravoos.ai.controller;

import com.pravoos.ai.model.dto.ChatRequest;
import com.pravoos.ai.model.dto.ChatResponse;
import com.pravoos.ai.model.dto.ConversationResponse;
import com.pravoos.ai.model.dto.MessageResponse;
import com.pravoos.ai.model.dto.RateRequest;
import com.pravoos.common.web.SecurityUtils;
import com.pravoos.ai.service.ChatService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
        return ResponseEntity.ok(chatService.chat(request, SecurityUtils.currentUserId(authentication)));
    }

    @GetMapping("/conversations")
    public ResponseEntity<List<ConversationResponse>> getConversations(
            @RequestParam(required = false) String q,
            Authentication authentication) {
        return ResponseEntity.ok(chatService.getConversations(SecurityUtils.currentUserId(authentication), q));
    }

    @GetMapping("/conversations/{id}/messages")
    public ResponseEntity<List<MessageResponse>> getMessages(
            @PathVariable String id,
            Authentication authentication) {
        return ResponseEntity.ok(chatService.getMessages(id, SecurityUtils.currentUserId(authentication)));
    }

    @PostMapping("/messages/{id}/rate")
    public ResponseEntity<MessageResponse> rateMessage(
            @PathVariable String id,
            @Valid @RequestBody RateRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(chatService.rateMessage(id, request, SecurityUtils.currentUserId(authentication)));
    }
}
