package com.pravoos.ai.practice.internal.controller;

import com.pravoos.ai.practice.internal.dto.CaseDraftDto;
import com.pravoos.ai.practice.internal.dto.CreateTemplateRequest;
import com.pravoos.ai.practice.internal.dto.TemplateResponse;
import com.pravoos.ai.practice.internal.dto.UpdateTemplateRequest;
import com.pravoos.ai.practice.internal.service.TemplateService;
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
public class TemplateController {

  private final TemplateService templateService;

  public TemplateController(TemplateService templateService) {
    this.templateService = templateService;
  }

  @PostMapping("/api/ai/templates")
  public ResponseEntity<TemplateResponse> create(
      @Valid @RequestBody CreateTemplateRequest request, CallerContext caller) {
    UUID lawyerId = caller.userId();
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(templateService.create(request, lawyerId));
  }

  @GetMapping("/api/ai/templates")
  public ResponseEntity<List<TemplateResponse>> list(CallerContext caller) {
    return ResponseEntity.ok(templateService.findByLawyer(caller.userId()));
  }

  @GetMapping("/api/ai/templates/{templateId}")
  public ResponseEntity<TemplateResponse> get(@PathVariable UUID templateId, CallerContext caller) {
    return ResponseEntity.ok(templateService.get(templateId, caller.userId()));
  }

  @PutMapping("/api/ai/templates/{templateId}")
  public ResponseEntity<TemplateResponse> update(
      @PathVariable UUID templateId,
      @Valid @RequestBody UpdateTemplateRequest request,
      CallerContext caller) {
    UUID lawyerId = caller.userId();
    return ResponseEntity.ok(templateService.update(templateId, request, lawyerId));
  }

  @DeleteMapping("/api/ai/templates/{templateId}")
  public ResponseEntity<Void> delete(@PathVariable UUID templateId, Authentication authentication) {
    templateService.delete(templateId, DeletionActor.of(authentication));
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/api/ai/cases/{caseId}/templates/{templateId}/apply")
  public ResponseEntity<CaseDraftDto> applyToCase(
      @PathVariable UUID caseId, @PathVariable UUID templateId, CallerContext caller) {
    UUID lawyerId = caller.userId();
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(templateService.applyToCase(caseId, templateId, lawyerId, caller.orgIds()));
  }
}
