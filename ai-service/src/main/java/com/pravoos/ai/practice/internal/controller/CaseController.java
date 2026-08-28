package com.pravoos.ai.practice.internal.controller;

import com.pravoos.ai.core.api.AiResponseDto;
import com.pravoos.ai.core.api.AiResponseQuery;
import com.pravoos.ai.document.api.DocumentResponse;
import com.pravoos.ai.document.api.DocumentUploadResponse;
import com.pravoos.ai.practice.internal.dto.*;
import com.pravoos.ai.practice.internal.service.CaseAnalyticsService;
import com.pravoos.ai.practice.internal.service.CaseExportService;
import com.pravoos.ai.practice.internal.service.CaseService;
import com.pravoos.ai.practice.internal.service.CaseTaskService;
import com.pravoos.ai.practice.internal.service.WorkflowExecutionService;
import com.pravoos.ai.practice.internal.service.WorkflowService;
import com.pravoos.ai.recyclebin.api.DeletionActor;
import com.pravoos.ai.shared.model.enums.CaseStatus;
import com.pravoos.ai.shared.util.PagedResponse;
import com.pravoos.ai.shared.util.SecureFileHeaders;
import com.pravoos.common.web.SecurityUtils;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/ai/cases")
public class CaseController {

  private final CaseService caseService;
  private final WorkflowService workflowService;
  private final WorkflowExecutionService workflowExecutionService;
  private final AiResponseQuery aiResponseQuery;
  private final CaseExportService caseExportService;
  private final CaseTaskService caseTaskService;
  private final CaseAnalyticsService caseAnalyticsService;

  public CaseController(
      CaseService caseService,
      WorkflowService workflowService,
      WorkflowExecutionService workflowExecutionService,
      AiResponseQuery aiResponseQuery,
      CaseExportService caseExportService,
      CaseTaskService caseTaskService,
      CaseAnalyticsService caseAnalyticsService) {
    this.caseService = caseService;
    this.workflowService = workflowService;
    this.workflowExecutionService = workflowExecutionService;
    this.aiResponseQuery = aiResponseQuery;
    this.caseExportService = caseExportService;
    this.caseTaskService = caseTaskService;
    this.caseAnalyticsService = caseAnalyticsService;
  }

  @PostMapping
  public ResponseEntity<CaseResponse> create(
      @Valid @RequestBody CreateCaseRequest request, Authentication authentication) {
    UUID lawyerId = SecurityUtils.currentUserId(authentication);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(caseService.create(request, lawyerId, SecurityUtils.currentOrgIds(authentication)));
  }

  @GetMapping
  public ResponseEntity<List<CaseResponse>> list(
      @RequestParam(required = false) CaseStatus status,
      @RequestParam(required = false) UUID orgId,
      @RequestParam(required = false) String q,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      Authentication authentication) {
    return PagedResponse.of(
        caseService.findByLawyer(
            SecurityUtils.currentUserId(authentication),
            SecurityUtils.currentOrgIds(authentication),
            status,
            orgId,
            q,
            page,
            size));
  }

  @GetMapping("/{caseId}")
  public ResponseEntity<CaseResponse> get(
      @PathVariable UUID caseId, Authentication authentication) {
    return ResponseEntity.ok(
        caseService.get(
            caseId,
            SecurityUtils.currentUserId(authentication),
            SecurityUtils.currentOrgIds(authentication)));
  }

  @PatchMapping("/{caseId}")
  public ResponseEntity<CaseResponse> update(
      @PathVariable UUID caseId,
      @Valid @RequestBody UpdateCaseRequest request,
      Authentication authentication) {
    UUID lawyerId = SecurityUtils.currentUserId(authentication);
    return ResponseEntity.ok(
        caseService.update(caseId, request, lawyerId, SecurityUtils.currentOrgIds(authentication)));
  }

  @PatchMapping("/{caseId}/status")
  public ResponseEntity<CaseResponse> updateStatus(
      @PathVariable UUID caseId,
      @Valid @RequestBody UpdateCaseStatusRequest request,
      Authentication authentication) {
    UUID lawyerId = SecurityUtils.currentUserId(authentication);
    return ResponseEntity.ok(
        caseService.updateStatus(
            caseId, request.status(), lawyerId, SecurityUtils.currentOrgIds(authentication)));
  }

  @PatchMapping("/{caseId}/org")
  public ResponseEntity<CaseResponse> changeOrg(
      @PathVariable UUID caseId,
      @RequestBody ChangeCaseOrgRequest request,
      Authentication authentication) {
    UUID lawyerId = SecurityUtils.currentUserId(authentication);
    return ResponseEntity.ok(
        caseService.changeOrg(
            caseId, request.orgId(), lawyerId, SecurityUtils.currentOrgIds(authentication)));
  }

  @PatchMapping("/{caseId}/owner")
  public ResponseEntity<CaseResponse> transferOwner(
      @PathVariable UUID caseId,
      @Valid @RequestBody TransferCaseOwnerRequest request,
      Authentication authentication) {
    UUID lawyerId = SecurityUtils.currentUserId(authentication);
    return ResponseEntity.ok(caseService.transferOwner(caseId, request.newOwnerId(), lawyerId));
  }

  @DeleteMapping("/{caseId}")
  public ResponseEntity<Void> delete(@PathVariable UUID caseId, Authentication authentication) {
    caseService.delete(caseId, DeletionActor.of(authentication));
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
        .body(
            caseService.uploadDocument(
                caseId, file, title, lawyerId, SecurityUtils.currentOrgIds(authentication)));
  }

  @GetMapping("/{caseId}/documents")
  public ResponseEntity<List<DocumentResponse>> documents(
      @PathVariable UUID caseId, Authentication authentication) {
    return ResponseEntity.ok(
        caseService.findDocuments(
            caseId,
            SecurityUtils.currentUserId(authentication),
            SecurityUtils.currentOrgIds(authentication)));
  }

  @PatchMapping("/{caseId}/documents/{documentId}/visibility")
  public ResponseEntity<DocumentResponse> setDocumentVisibility(
      @PathVariable UUID caseId,
      @PathVariable UUID documentId,
      @RequestBody DocumentVisibilityRequest request,
      Authentication authentication) {
    UUID lawyerId = SecurityUtils.currentUserId(authentication);
    return ResponseEntity.ok(
        caseService.setDocumentVisibility(
            caseId,
            documentId,
            request.visibleToClient(),
            lawyerId,
            SecurityUtils.currentOrgIds(authentication)));
  }

  @GetMapping("/{caseId}/hearings")
  public ResponseEntity<List<CaseHearingEventResponse>> hearings(
      @PathVariable UUID caseId, Authentication authentication) {
    return ResponseEntity.ok(
        caseService.findHearingEvents(
            caseId,
            SecurityUtils.currentUserId(authentication),
            SecurityUtils.currentOrgIds(authentication)));
  }

  @PostMapping("/{caseId}/court/sync")
  public ResponseEntity<List<CaseHearingEventResponse>> syncCourt(
      @PathVariable UUID caseId, Authentication authentication) {
    UUID lawyerId = SecurityUtils.currentUserId(authentication);
    return ResponseEntity.ok(
        caseService.syncCourt(caseId, lawyerId, SecurityUtils.currentOrgIds(authentication)));
  }

  @PostMapping("/{caseId}/workflows/{workflowId}/run")
  public ResponseEntity<AiResponseDto> runWorkflow(
      @PathVariable UUID caseId,
      @PathVariable String workflowId,
      @Valid @RequestBody(required = false) RunWorkflowRequest request,
      Authentication authentication) {
    UUID lawyerId = SecurityUtils.currentUserId(authentication);
    return ResponseEntity.ok(
        workflowService.run(
            caseId, workflowId, request, lawyerId, SecurityUtils.currentOrgIds(authentication)));
  }

  @PostMapping("/{caseId}/workflow-runs")
  public ResponseEntity<WorkflowRunDto> runWorkflowDefinition(
      @PathVariable UUID caseId,
      @Valid @RequestBody RunWorkflowDefinitionRequest request,
      Authentication authentication) {
    UUID lawyerId = SecurityUtils.currentUserId(authentication);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            workflowExecutionService.run(
                caseId,
                request.definitionId(),
                lawyerId,
                SecurityUtils.currentOrgIds(authentication)));
  }

  @GetMapping("/{caseId}/workflow-runs")
  public ResponseEntity<List<WorkflowRunDto>> workflowRuns(
      @PathVariable UUID caseId, Authentication authentication) {
    return ResponseEntity.ok(
        workflowExecutionService.listRuns(
            caseId,
            SecurityUtils.currentUserId(authentication),
            SecurityUtils.currentOrgIds(authentication)));
  }

  @GetMapping("/{caseId}/workflow-runs/{runId}")
  public ResponseEntity<WorkflowRunDto> workflowRun(
      @PathVariable UUID caseId, @PathVariable UUID runId, Authentication authentication) {
    return ResponseEntity.ok(
        workflowExecutionService.getRun(
            caseId,
            runId,
            SecurityUtils.currentUserId(authentication),
            SecurityUtils.currentOrgIds(authentication)));
  }

  @GetMapping("/{caseId}/analytics")
  public ResponseEntity<CaseAnalyticsResponse> analytics(
      @PathVariable UUID caseId, Authentication authentication) {
    return ResponseEntity.ok(
        caseAnalyticsService.getAnalytics(
            caseId,
            SecurityUtils.currentUserId(authentication),
            SecurityUtils.currentOrgIds(authentication)));
  }

  @PostMapping("/{caseId}/analytics/generate")
  public ResponseEntity<CaseAnalyticsResponse> generateAnalytics(
      @PathVariable UUID caseId, Authentication authentication) {
    return ResponseEntity.ok(
        caseAnalyticsService.generateAnalysis(
            caseId,
            SecurityUtils.currentUserId(authentication),
            SecurityUtils.currentOrgIds(authentication)));
  }

  @GetMapping("/{caseId}/responses")
  public ResponseEntity<List<AiResponseDto>> responses(
      @PathVariable UUID caseId, Authentication authentication) {
    return ResponseEntity.ok(
        aiResponseQuery.listVisibleByCase(
            caseId,
            SecurityUtils.currentUserId(authentication),
            SecurityUtils.currentOrgIds(authentication)));
  }

  @GetMapping("/{caseId}/tasks")
  public ResponseEntity<List<CaseTaskResponse>> tasks(
      @PathVariable UUID caseId, Authentication authentication) {
    return ResponseEntity.ok(
        caseTaskService.findByCase(
            caseId,
            SecurityUtils.currentUserId(authentication),
            SecurityUtils.currentOrgIds(authentication)));
  }

  @PostMapping("/{caseId}/tasks")
  public ResponseEntity<CaseTaskResponse> createTask(
      @PathVariable UUID caseId,
      @Valid @RequestBody CreateCaseTaskRequest request,
      Authentication authentication) {
    UUID lawyerId = SecurityUtils.currentUserId(authentication);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            caseTaskService.create(
                caseId, request, lawyerId, SecurityUtils.currentOrgIds(authentication)));
  }

  @PatchMapping("/{caseId}/tasks/{taskId}")
  public ResponseEntity<CaseTaskResponse> updateTask(
      @PathVariable UUID caseId,
      @PathVariable UUID taskId,
      @Valid @RequestBody UpdateCaseTaskRequest request,
      Authentication authentication) {
    UUID lawyerId = SecurityUtils.currentUserId(authentication);
    return ResponseEntity.ok(
        caseTaskService.update(
            caseId, taskId, request, lawyerId, SecurityUtils.currentOrgIds(authentication)));
  }

  @DeleteMapping("/{caseId}/tasks/{taskId}")
  public ResponseEntity<Void> deleteTask(
      @PathVariable UUID caseId, @PathVariable UUID taskId, Authentication authentication) {
    caseTaskService.delete(
        caseId,
        taskId,
        SecurityUtils.currentUserId(authentication),
        SecurityUtils.currentOrgIds(authentication));
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{caseId}/tasks/generate")
  public ResponseEntity<List<CaseTaskResponse>> generateTasks(
      @PathVariable UUID caseId, Authentication authentication) {
    UUID lawyerId = SecurityUtils.currentUserId(authentication);
    return ResponseEntity.ok(
        caseTaskService.generateFromChecklist(
            caseId, lawyerId, SecurityUtils.currentOrgIds(authentication)));
  }

  @GetMapping("/{caseId}/export")
  public ResponseEntity<byte[]> export(
      @PathVariable UUID caseId,
      @RequestParam(defaultValue = "docx") String format,
      Authentication authentication) {
    UUID lawyerId = SecurityUtils.currentUserId(authentication);
    ExportedFile file =
        caseExportService.export(
            caseId, lawyerId, format, SecurityUtils.currentOrgIds(authentication));

    HttpHeaders headers = new HttpHeaders();
    headers.setContentDisposition(
        ContentDisposition.attachment().filename(file.fileName(), StandardCharsets.UTF_8).build());
    SecureFileHeaders.apply(headers);

    return ResponseEntity.ok()
        .headers(headers)
        .contentType(MediaType.parseMediaType(file.contentType()))
        .body(file.content());
  }
}
