package com.pravoos.ai.core.internal.controller;

import com.pravoos.ai.core.api.*;
import com.pravoos.ai.core.internal.dto.*;
import com.pravoos.ai.core.internal.service.ChatService;
import com.pravoos.ai.shared.util.PagedResponse;
import com.pravoos.common.web.SecurityUtils;
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
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            Authentication authentication) {
        return PagedResponse.of(
                chatService.getConversations(SecurityUtils.currentUserId(authentication), q, page, size));
    }

    @GetMapping("/conversations/{id}/messages")
    public ResponseEntity<List<MessageResponse>> getMessages(
            @PathVariable String id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            Authentication authentication) {
        return PagedResponse.of(
                chatService.getMessages(id, SecurityUtils.currentUserId(authentication), page, size));
    }

    @PostMapping("/messages/{id}/rate")
    public ResponseEntity<MessageResponse> rateMessage(
            @PathVariable String id,
            @Valid @RequestBody RateRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(chatService.rateMessage(id, request, SecurityUtils.currentUserId(authentication)));
    }
}
