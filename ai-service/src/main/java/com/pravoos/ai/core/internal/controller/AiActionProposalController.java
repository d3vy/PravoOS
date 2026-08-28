package com.pravoos.ai.core.internal.controller;

import com.pravoos.ai.core.api.AiActorRole;
import com.pravoos.ai.core.api.AiToolContext;
import com.pravoos.ai.core.internal.dto.AiActionProposalResponse;
import com.pravoos.ai.core.internal.service.AiActionProposalService;
import com.pravoos.common.web.SecurityUtils;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai/chat/proposals")
public class AiActionProposalController {

  private final AiActionProposalService proposalService;

  public AiActionProposalController(AiActionProposalService proposalService) {
    this.proposalService = proposalService;
  }

  @GetMapping
  public ResponseEntity<List<AiActionProposalResponse>> pending(
      @RequestParam String conversationId, Authentication authentication) {
    return ResponseEntity.ok(proposalService.pending(actor(authentication, conversationId)));
  }

  @PostMapping("/{proposalId}/approve")
  public ResponseEntity<AiActionProposalResponse> approve(
      @PathVariable UUID proposalId,
      @RequestParam(defaultValue = "false") boolean alwaysAllow,
      Authentication authentication) {
    return ResponseEntity.ok(
        proposalService.approve(proposalId, actor(authentication, null), alwaysAllow));
  }

  @PostMapping("/{proposalId}/reject")
  public ResponseEntity<AiActionProposalResponse> reject(
      @PathVariable UUID proposalId, Authentication authentication) {
    return ResponseEntity.ok(proposalService.reject(proposalId, actor(authentication, null)));
  }

  private static AiToolContext actor(Authentication authentication, String conversationId) {
    return new AiToolContext(
        SecurityUtils.currentUserId(authentication),
        SecurityUtils.currentOrgIds(authentication),
        AiActorRole.of(authentication),
        conversationId);
  }
}
