package com.pravoos.ai.controller;

import com.pravoos.ai.model.dto.*;
import com.pravoos.ai.model.enums.CaseStatus;
import com.pravoos.ai.security.SecurityUtils;
import com.pravoos.ai.service.AiResponseService;
import com.pravoos.ai.service.CaseExportService;
import com.pravoos.ai.service.CaseService;
import com.pravoos.ai.service.WorkflowService;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/ai/cases")
public class CaseController {

    private final CaseService caseService;
    private final WorkflowService workflowService;
    private final AiResponseService aiResponseService;
    private final CaseExportService caseExportService;

    public CaseController(CaseService caseService,
                          WorkflowService workflowService,
                          AiResponseService aiResponseService,
                          CaseExportService caseExportService) {
        this.caseService = caseService;
        this.workflowService = workflowService;
        this.aiResponseService = aiResponseService;
        this.caseExportService = caseExportService;
    }

    @PostMapping
    public ResponseEntity<CaseResponse> create(@Valid @RequestBody CreateCaseRequest request,
                                               Authentication authentication) {
        UUID lawyerId = SecurityUtils.currentUserId(authentication);
        return ResponseEntity.status(HttpStatus.CREATED).body(caseService.create(request, lawyerId));
    }

    @GetMapping
    public ResponseEntity<List<CaseResponse>> list(@RequestParam(required = false) CaseStatus status,
                                                   Authentication authentication) {
        return ResponseEntity.ok(caseService.findByLawyer(SecurityUtils.currentUserId(authentication), status));
    }

    @GetMapping("/{caseId}")
    public ResponseEntity<CaseResponse> get(@PathVariable UUID caseId, Authentication authentication) {
        return ResponseEntity.ok(caseService.get(caseId, SecurityUtils.currentUserId(authentication)));
    }

    @PatchMapping("/{caseId}")
    public ResponseEntity<CaseResponse> update(@PathVariable UUID caseId,
                                               @Valid @RequestBody UpdateCaseRequest request,
                                               Authentication authentication) {
        UUID lawyerId = SecurityUtils.currentUserId(authentication);
        return ResponseEntity.ok(caseService.update(caseId, request, lawyerId));
    }

    @PatchMapping("/{caseId}/status")
    public ResponseEntity<CaseResponse> updateStatus(@PathVariable UUID caseId,
                                                     @Valid @RequestBody UpdateCaseStatusRequest request,
                                                     Authentication authentication) {
        UUID lawyerId = SecurityUtils.currentUserId(authentication);
        return ResponseEntity.ok(caseService.updateStatus(caseId, request.status(), lawyerId));
    }

    @DeleteMapping("/{caseId}")
    public ResponseEntity<Void> delete(@PathVariable UUID caseId, Authentication authentication) {
        caseService.delete(caseId, SecurityUtils.currentUserId(authentication));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{caseId}/documents")
    public ResponseEntity<DocumentUploadResponse> uploadDocument(
            @PathVariable UUID caseId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "title", required = false) String title,
            Authentication authentication) {
        UUID lawyerId = SecurityUtils.currentUserId(authentication);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(caseService.uploadDocument(caseId, file, title, lawyerId));
    }

    @GetMapping("/{caseId}/documents")
    public ResponseEntity<List<DocumentResponse>> documents(@PathVariable UUID caseId,
                                                            Authentication authentication) {
        return ResponseEntity.ok(caseService.findDocuments(caseId, SecurityUtils.currentUserId(authentication)));
    }

    @PostMapping("/{caseId}/workflows/{workflowId}/run")
    public ResponseEntity<AiResponseDto> runWorkflow(
            @PathVariable UUID caseId,
            @PathVariable String workflowId,
            @Valid @RequestBody(required = false) RunWorkflowRequest request,
            Authentication authentication) {
        UUID lawyerId = SecurityUtils.currentUserId(authentication);
        return ResponseEntity.ok(workflowService.run(caseId, workflowId, request, lawyerId));
    }

    @GetMapping("/{caseId}/responses")
    public ResponseEntity<List<AiResponseDto>> responses(@PathVariable UUID caseId,
                                                         Authentication authentication) {
        return ResponseEntity.ok(aiResponseService.findByCase(caseId, SecurityUtils.currentUserId(authentication)));
    }

    @GetMapping("/{caseId}/export")
    public ResponseEntity<byte[]> export(@PathVariable UUID caseId,
                                         @RequestParam(defaultValue = "docx") String format,
                                         Authentication authentication) {
        UUID lawyerId = SecurityUtils.currentUserId(authentication);
        ExportedFile file = caseExportService.export(caseId, lawyerId, format);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(
                ContentDisposition.attachment().filename(file.fileName(), StandardCharsets.UTF_8).build());

        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType(file.contentType()))
                .body(file.content());
    }
}
