package com.pravoos.ai.core.internal.controller;

import com.pravoos.ai.core.internal.dto.*;
import com.pravoos.ai.core.internal.service.ChatAttachmentService;
import com.pravoos.ai.core.internal.service.ChatService;
import com.pravoos.ai.document.api.DocumentResponse;
import com.pravoos.ai.document.api.DocumentUploadResponse;
import com.pravoos.ai.shared.util.PagedResponse;
import com.pravoos.common.web.SecurityUtils;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/ai")
public class ChatController {

  private final ChatService chatService;
  private final ChatAttachmentService chatAttachmentService;

  public ChatController(ChatService chatService, ChatAttachmentService chatAttachmentService) {
    this.chatService = chatService;
    this.chatAttachmentService = chatAttachmentService;
  }

  @PostMapping("/chat/attachments")
  public ResponseEntity<DocumentUploadResponse> uploadAttachment(
      @RequestParam("file") MultipartFile file,
      @RequestParam(value = "title", required = false) String title,
      Authentication authentication) {
    UUID lawyerId = SecurityUtils.currentUserId(authentication);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(chatAttachmentService.upload(file, title, lawyerId));
  }

  @GetMapping("/chat/attachments")
  public ResponseEntity<List<DocumentResponse>> getAttachments(Authentication authentication) {
    return ResponseEntity.ok(
        chatAttachmentService.findAll(SecurityUtils.currentUserId(authentication)));
  }

  @PostMapping("/chat")
  public ResponseEntity<ChatResponse> chat(
      @Valid @RequestBody ChatRequest request, Authentication authentication) {
    return ResponseEntity.ok(
        chatService.chat(request, SecurityUtils.currentUserId(authentication)));
  }

  @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter chatStream(
      @Valid @RequestBody ChatRequest request,
      Authentication authentication,
      HttpServletResponse response) {
    response.setHeader("X-Accel-Buffering", "no");
    response.setHeader("Cache-Control", "no-cache");
    return chatService.chatStream(request, SecurityUtils.currentUserId(authentication));
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
    return ResponseEntity.ok(
        chatService.rateMessage(id, request, SecurityUtils.currentUserId(authentication)));
  }
}
