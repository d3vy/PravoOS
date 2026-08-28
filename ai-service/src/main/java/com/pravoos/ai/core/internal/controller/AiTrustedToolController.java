package com.pravoos.ai.core.internal.controller;

import com.pravoos.ai.core.api.AiActorRole;
import com.pravoos.ai.core.api.AiToolContext;
import com.pravoos.ai.core.internal.dto.AiTrustedToolResponse;
import com.pravoos.ai.core.internal.service.AiActionProposalService;
import com.pravoos.common.web.SecurityUtils;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai/chat/trusted-tools")
public class AiTrustedToolController {

  private final AiActionProposalService proposalService;

  public AiTrustedToolController(AiActionProposalService proposalService) {
    this.proposalService = proposalService;
  }

  @GetMapping
  public ResponseEntity<List<AiTrustedToolResponse>> list(Authentication authentication) {
    return ResponseEntity.ok(proposalService.trusted(actor(authentication)));
  }

  @DeleteMapping("/{toolName}")
  public ResponseEntity<Void> revoke(@PathVariable String toolName, Authentication authentication) {
    proposalService.revoke(toolName, actor(authentication));
    return ResponseEntity.noContent().build();
  }

  private static AiToolContext actor(Authentication authentication) {
    return new AiToolContext(
        SecurityUtils.currentUserId(authentication),
        SecurityUtils.currentOrgIds(authentication),
        AiActorRole.of(authentication));
  }
}
