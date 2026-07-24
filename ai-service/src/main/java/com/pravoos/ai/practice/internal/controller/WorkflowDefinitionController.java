package com.pravoos.ai.practice.internal.controller;

import com.pravoos.ai.practice.internal.dto.SaveWorkflowDefinitionRequest;
import com.pravoos.ai.practice.internal.dto.WorkflowDefinitionDto;
import com.pravoos.ai.practice.internal.service.WorkflowDefinitionService;
import com.pravoos.common.web.SecurityUtils;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai/workflow-definitions")
public class WorkflowDefinitionController {

  private final WorkflowDefinitionService definitionService;

  public WorkflowDefinitionController(WorkflowDefinitionService definitionService) {
    this.definitionService = definitionService;
  }

  @GetMapping
  public ResponseEntity<List<WorkflowDefinitionDto>> list(Authentication authentication) {
    return ResponseEntity.ok(
        definitionService.listVisible(
            SecurityUtils.currentUserId(authentication),
            SecurityUtils.currentOrgIds(authentication)));
  }

  @GetMapping("/{id}")
  public ResponseEntity<WorkflowDefinitionDto> get(
      @PathVariable UUID id, Authentication authentication) {
    return ResponseEntity.ok(
        definitionService.get(
            id,
            SecurityUtils.currentUserId(authentication),
            SecurityUtils.currentOrgIds(authentication)));
  }

  @PostMapping
  public ResponseEntity<WorkflowDefinitionDto> create(
      @Valid @RequestBody SaveWorkflowDefinitionRequest request, Authentication authentication) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(definitionService.create(request, SecurityUtils.currentUserId(authentication)));
  }

  @PutMapping("/{id}")
  public ResponseEntity<WorkflowDefinitionDto> update(
      @PathVariable UUID id,
      @Valid @RequestBody SaveWorkflowDefinitionRequest request,
      Authentication authentication) {
    return ResponseEntity.ok(
        definitionService.update(
            id,
            request,
            SecurityUtils.currentUserId(authentication),
            SecurityUtils.currentOrgIds(authentication)));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable UUID id, Authentication authentication) {
    definitionService.delete(
        id,
        SecurityUtils.currentUserId(authentication),
        SecurityUtils.currentOrgIds(authentication));
    return ResponseEntity.noContent().build();
  }
}
