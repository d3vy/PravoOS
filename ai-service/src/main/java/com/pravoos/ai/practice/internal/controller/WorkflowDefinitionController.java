package com.pravoos.ai.practice.internal.controller;

import com.pravoos.ai.practice.internal.dto.SaveWorkflowDefinitionRequest;
import com.pravoos.ai.practice.internal.dto.WorkflowDefinitionDto;
import com.pravoos.ai.practice.internal.service.WorkflowDefinitionService;
import com.pravoos.ai.recyclebin.api.DeletionActor;
import com.pravoos.ai.shared.security.CallerContext;
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
  public ResponseEntity<List<WorkflowDefinitionDto>> list(CallerContext caller) {
    return ResponseEntity.ok(definitionService.listVisible(caller.userId(), caller.orgIds()));
  }

  @GetMapping("/{id}")
  public ResponseEntity<WorkflowDefinitionDto> get(@PathVariable UUID id, CallerContext caller) {
    return ResponseEntity.ok(definitionService.get(id, caller.userId(), caller.orgIds()));
  }

  @PostMapping
  public ResponseEntity<WorkflowDefinitionDto> create(
      @Valid @RequestBody SaveWorkflowDefinitionRequest request, CallerContext caller) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(definitionService.create(request, caller.userId()));
  }

  @PutMapping("/{id}")
  public ResponseEntity<WorkflowDefinitionDto> update(
      @PathVariable UUID id,
      @Valid @RequestBody SaveWorkflowDefinitionRequest request,
      CallerContext caller) {
    return ResponseEntity.ok(
        definitionService.update(id, request, caller.userId(), caller.orgIds()));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable UUID id, Authentication authentication) {
    definitionService.delete(id, DeletionActor.of(authentication));
    return ResponseEntity.noContent().build();
  }
}
