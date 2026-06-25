package com.pravoos.ai.controller;

import com.pravoos.ai.model.dto.CaseDraftDto;
import com.pravoos.ai.model.dto.CreateTemplateRequest;
import com.pravoos.ai.model.dto.TemplateResponse;
import com.pravoos.ai.model.dto.UpdateTemplateRequest;
import com.pravoos.common.web.SecurityUtils;
import com.pravoos.ai.service.TemplateService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
public class TemplateController {

    private final TemplateService templateService;

    public TemplateController(TemplateService templateService) {
        this.templateService = templateService;
    }

    @PostMapping("/api/ai/templates")
    public ResponseEntity<TemplateResponse> create(@Valid @RequestBody CreateTemplateRequest request,
                                                   Authentication authentication) {
        UUID lawyerId = SecurityUtils.currentUserId(authentication);
        return ResponseEntity.status(HttpStatus.CREATED).body(templateService.create(request, lawyerId));
    }

    @GetMapping("/api/ai/templates")
    public ResponseEntity<List<TemplateResponse>> list(Authentication authentication) {
        return ResponseEntity.ok(templateService.findByLawyer(SecurityUtils.currentUserId(authentication)));
    }

    @GetMapping("/api/ai/templates/{templateId}")
    public ResponseEntity<TemplateResponse> get(@PathVariable UUID templateId,
                                               Authentication authentication) {
        return ResponseEntity.ok(templateService.get(templateId, SecurityUtils.currentUserId(authentication)));
    }

    @PutMapping("/api/ai/templates/{templateId}")
    public ResponseEntity<TemplateResponse> update(@PathVariable UUID templateId,
                                                  @Valid @RequestBody UpdateTemplateRequest request,
                                                  Authentication authentication) {
        UUID lawyerId = SecurityUtils.currentUserId(authentication);
        return ResponseEntity.ok(templateService.update(templateId, request, lawyerId));
    }

    @DeleteMapping("/api/ai/templates/{templateId}")
    public ResponseEntity<Void> delete(@PathVariable UUID templateId,
                                       Authentication authentication) {
        templateService.delete(templateId, SecurityUtils.currentUserId(authentication));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/ai/cases/{caseId}/templates/{templateId}/apply")
    public ResponseEntity<CaseDraftDto> applyToCase(@PathVariable UUID caseId,
                                                   @PathVariable UUID templateId,
                                                   Authentication authentication) {
        UUID lawyerId = SecurityUtils.currentUserId(authentication);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(templateService.applyToCase(caseId, templateId, lawyerId));
    }
}
