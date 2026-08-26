package com.pravoos.ai.core.internal.controller;

import com.pravoos.ai.core.internal.dto.AdminConversationResponse;
import com.pravoos.ai.core.internal.dto.MessageResponse;
import com.pravoos.ai.core.internal.service.AdminConversationService;
import com.pravoos.ai.shared.model.enums.AuditAction;
import com.pravoos.ai.shared.service.AccessAuditService;
import com.pravoos.ai.shared.util.PagedResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai/admin/conversations")
public class AdminConversationController {

  private final AdminConversationService adminConversationService;
  private final AccessAuditService accessAuditService;

  public AdminConversationController(
      AdminConversationService adminConversationService, AccessAuditService accessAuditService) {
    this.adminConversationService = adminConversationService;
    this.accessAuditService = accessAuditService;
  }

  @GetMapping
  public ResponseEntity<List<AdminConversationResponse>> getConversations(
      @RequestParam(required = false) UUID orgId,
      @RequestParam(required = false) UUID lawyerId,
      @RequestParam(required = false) String q,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return PagedResponse.of(
        adminConversationService.getConversations(orgId, lawyerId, q, page, size));
  }

  @GetMapping("/{id}/messages")
  public ResponseEntity<List<MessageResponse>> getMessages(
      @PathVariable String id,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size,
      Authentication authentication,
      HttpServletRequest request) {
    ResponseEntity<List<MessageResponse>> response =
        PagedResponse.of(adminConversationService.getMessages(id, page, size));
    accessAuditService.recordRef(authentication, AuditAction.AI_CONVERSATION_VIEW, id, request);
    return response;
  }
}
