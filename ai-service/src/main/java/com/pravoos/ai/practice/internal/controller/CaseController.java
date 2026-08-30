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
import com.pravoos.ai.shared.security.CallerContext;
import com.pravoos.ai.shared.util.PagedResponse;
import com.pravoos.ai.shared.util.SecureFileHeaders;
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
      @Valid @RequestBody CreateCaseRequest request, CallerContext caller) {
    UUID lawyerId = caller.userId();
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(caseService.create(request, lawyerId, caller.orgIds()));
  }

  @GetMapping
  public ResponseEntity<List<CaseResponse>> list(
      @RequestParam(required = false) CaseStatus status,
      @RequestParam(required = false) UUID orgId,
      @RequestParam(required = false) String q,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      CallerContext caller) {
    return PagedResponse.of(
        caseService.findByLawyer(caller.userId(), caller.orgIds(), status, orgId, q, page, size));
  }

  @GetMapping("/{caseId}")
  public ResponseEntity<CaseResponse> get(@PathVariable UUID caseId, CallerContext caller) {
    return ResponseEntity.ok(caseService.get(caseId, caller.userId(), caller.orgIds()));
  }

  @PatchMapping("/{caseId}")
  public ResponseEntity<CaseResponse> update(
      @PathVariable UUID caseId,
      @Valid @RequestBody UpdateCaseRequest request,
      CallerContext caller) {
    UUID lawyerId = caller.userId();
    return ResponseEntity.ok(caseService.update(caseId, request, lawyerId, caller.orgIds()));
  }

  @PatchMapping("/{caseId}/status")
  public ResponseEntity<CaseResponse> updateStatus(
      @PathVariable UUID caseId,
      @Valid @RequestBody UpdateCaseStatusRequest request,
      CallerContext caller) {
    UUID lawyerId = caller.userId();
    return ResponseEntity.ok(
        caseService.updateStatus(caseId, request.status(), lawyerId, caller.orgIds()));
  }

  @PatchMapping("/{caseId}/org")
  public ResponseEntity<CaseResponse> changeOrg(
      @PathVariable UUID caseId, @RequestBody ChangeCaseOrgRequest request, CallerContext caller) {
    UUID lawyerId = caller.userId();
    return ResponseEntity.ok(
        caseService.changeOrg(caseId, request.orgId(), lawyerId, caller.orgIds()));
  }

  @PatchMapping("/{caseId}/owner")
  public ResponseEntity<CaseResponse> transferOwner(
      @PathVariable UUID caseId,
      @Valid @RequestBody TransferCaseOwnerRequest request,
      CallerContext caller) {
    UUID lawyerId = caller.userId();
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
      CallerContext caller) {
    UUID lawyerId = caller.userId();
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(caseService.uploadDocument(caseId, file, title, lawyerId, caller.orgIds()));
  }

  @GetMapping("/{caseId}/documents")
  public ResponseEntity<List<DocumentResponse>> documents(
      @PathVariable UUID caseId, CallerContext caller) {
    return ResponseEntity.ok(caseService.findDocuments(caseId, caller.userId(), caller.orgIds()));
  }

  @PatchMapping("/{caseId}/documents/{documentId}/visibility")
  public ResponseEntity<DocumentResponse> setDocumentVisibility(
      @PathVariable UUID caseId,
      @PathVariable UUID documentId,
      @RequestBody DocumentVisibilityRequest request,
      CallerContext caller) {
    UUID lawyerId = caller.userId();
    return ResponseEntity.ok(
        caseService.setDocumentVisibility(
            caseId, documentId, request.visibleToClient(), lawyerId, caller.orgIds()));
  }

  @GetMapping("/{caseId}/hearings")
  public ResponseEntity<List<CaseHearingEventResponse>> hearings(
      @PathVariable UUID caseId, CallerContext caller) {
    return ResponseEntity.ok(
        caseService.findHearingEvents(caseId, caller.userId(), caller.orgIds()));
  }

  @PostMapping("/{caseId}/court/sync")
  public ResponseEntity<List<CaseHearingEventResponse>> syncCourt(
      @PathVariable UUID caseId, CallerContext caller) {
    UUID lawyerId = caller.userId();
    return ResponseEntity.ok(caseService.syncCourt(caseId, lawyerId, caller.orgIds()));
  }

  @PostMapping("/{caseId}/workflows/{workflowId}/run")
  public ResponseEntity<AiResponseDto> runWorkflow(
      @PathVariable UUID caseId,
      @PathVariable String workflowId,
      @Valid @RequestBody(required = false) RunWorkflowRequest request,
      CallerContext caller) {
    UUID lawyerId = caller.userId();
    return ResponseEntity.ok(
        workflowService.run(caseId, workflowId, request, lawyerId, caller.orgIds()));
  }

  @PostMapping("/{caseId}/workflow-runs")
  public ResponseEntity<WorkflowRunDto> runWorkflowDefinition(
      @PathVariable UUID caseId,
      @Valid @RequestBody RunWorkflowDefinitionRequest request,
      CallerContext caller) {
    UUID lawyerId = caller.userId();
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            workflowExecutionService.run(
                caseId, request.definitionId(), lawyerId, caller.orgIds()));
  }

  @GetMapping("/{caseId}/workflow-runs")
  public ResponseEntity<List<WorkflowRunDto>> workflowRuns(
      @PathVariable UUID caseId, CallerContext caller) {
    return ResponseEntity.ok(
        workflowExecutionService.listRuns(caseId, caller.userId(), caller.orgIds()));
  }

  @GetMapping("/{caseId}/workflow-runs/{runId}")
  public ResponseEntity<WorkflowRunDto> workflowRun(
      @PathVariable UUID caseId, @PathVariable UUID runId, CallerContext caller) {
    return ResponseEntity.ok(
        workflowExecutionService.getRun(caseId, runId, caller.userId(), caller.orgIds()));
  }

  @GetMapping("/{caseId}/analytics")
  public ResponseEntity<CaseAnalyticsResponse> analytics(
      @PathVariable UUID caseId, CallerContext caller) {
    return ResponseEntity.ok(
        caseAnalyticsService.getAnalytics(caseId, caller.userId(), caller.orgIds()));
  }

  @PostMapping("/{caseId}/analytics/generate")
  public ResponseEntity<CaseAnalyticsResponse> generateAnalytics(
      @PathVariable UUID caseId, CallerContext caller) {
    return ResponseEntity.ok(
        caseAnalyticsService.generateAnalysis(caseId, caller.userId(), caller.orgIds()));
  }

  @GetMapping("/{caseId}/responses")
  public ResponseEntity<List<AiResponseDto>> responses(
      @PathVariable UUID caseId, CallerContext caller) {
    return ResponseEntity.ok(
        aiResponseQuery.listVisibleByCase(caseId, caller.userId(), caller.orgIds()));
  }

  @GetMapping("/{caseId}/tasks")
  public ResponseEntity<List<CaseTaskResponse>> tasks(
      @PathVariable UUID caseId, CallerContext caller) {
    return ResponseEntity.ok(caseTaskService.findByCase(caseId, caller.userId(), caller.orgIds()));
  }

  @PostMapping("/{caseId}/tasks")
  public ResponseEntity<CaseTaskResponse> createTask(
      @PathVariable UUID caseId,
      @Valid @RequestBody CreateCaseTaskRequest request,
      CallerContext caller) {
    UUID lawyerId = caller.userId();
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(caseTaskService.create(caseId, request, lawyerId, caller.orgIds()));
  }

  @PatchMapping("/{caseId}/tasks/{taskId}")
  public ResponseEntity<CaseTaskResponse> updateTask(
      @PathVariable UUID caseId,
      @PathVariable UUID taskId,
      @Valid @RequestBody UpdateCaseTaskRequest request,
      CallerContext caller) {
    UUID lawyerId = caller.userId();
    return ResponseEntity.ok(
        caseTaskService.update(caseId, taskId, request, lawyerId, caller.orgIds()));
  }

  @DeleteMapping("/{caseId}/tasks/{taskId}")
  public ResponseEntity<Void> deleteTask(
      @PathVariable UUID caseId, @PathVariable UUID taskId, CallerContext caller) {
    caseTaskService.delete(caseId, taskId, caller.userId(), caller.orgIds());
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{caseId}/tasks/generate")
  public ResponseEntity<List<CaseTaskResponse>> generateTasks(
      @PathVariable UUID caseId, CallerContext caller) {
    UUID lawyerId = caller.userId();
    return ResponseEntity.ok(
        caseTaskService.generateFromChecklist(caseId, lawyerId, caller.orgIds()));
  }

  @GetMapping("/{caseId}/export")
  public ResponseEntity<byte[]> export(
      @PathVariable UUID caseId,
      @RequestParam(defaultValue = "docx") String format,
      CallerContext caller) {
    UUID lawyerId = caller.userId();
    ExportedFile file = caseExportService.export(caseId, lawyerId, format, caller.orgIds());

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
