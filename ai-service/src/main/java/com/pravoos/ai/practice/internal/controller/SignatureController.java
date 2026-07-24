package com.pravoos.ai.practice.internal.controller;

import com.pravoos.ai.practice.internal.dto.CreateSignatureRequestDto;
import com.pravoos.ai.practice.internal.dto.SignatureRequestResponse;
import com.pravoos.ai.practice.internal.model.entity.Case;
import com.pravoos.ai.practice.internal.service.CaseService;
import com.pravoos.ai.practice.internal.service.SignatureService;
import com.pravoos.ai.practice.internal.service.SignatureService.SignatureFileDownload;
import com.pravoos.common.web.SecurityUtils;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai/cases/{caseId}/signatures")
public class SignatureController {

  private final SignatureService signatureService;
  private final CaseService caseService;

  public SignatureController(SignatureService signatureService, CaseService caseService) {
    this.signatureService = signatureService;
    this.caseService = caseService;
  }

  @PostMapping
  public ResponseEntity<SignatureRequestResponse> create(
      @PathVariable UUID caseId,
      @Valid @RequestBody CreateSignatureRequestDto request,
      Authentication authentication) {
    UUID lawyerId = SecurityUtils.currentUserId(authentication);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(signatureService.create(caseId, request, lawyerId));
  }

  @GetMapping
  public ResponseEntity<List<SignatureRequestResponse>> list(
      @PathVariable UUID caseId, Authentication authentication) {
    UUID lawyerId = SecurityUtils.currentUserId(authentication);
    return ResponseEntity.ok(
        signatureService.findByCase(caseId, lawyerId, SecurityUtils.currentOrgIds(authentication)));
  }

  @GetMapping("/{signatureId}/protocol")
  public ResponseEntity<Resource> exportProtocol(
      @PathVariable UUID caseId, @PathVariable UUID signatureId, Authentication authentication) {
    Case caseEntity =
        caseService.requireVisibleCase(
            caseId,
            SecurityUtils.currentUserId(authentication),
            SecurityUtils.currentOrgIds(authentication));
    byte[] protocol = signatureService.exportProtocol(caseEntity, signatureId);
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_PDF)
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"signature-protocol-" + signatureId + ".pdf\"")
        .contentLength(protocol.length)
        .body(new ByteArrayResource(protocol));
  }

  @GetMapping("/{signatureId}/signature-file")
  public ResponseEntity<Resource> downloadSignatureFile(
      @PathVariable UUID caseId, @PathVariable UUID signatureId, Authentication authentication) {
    Case caseEntity =
        caseService.requireVisibleCase(
            caseId,
            SecurityUtils.currentUserId(authentication),
            SecurityUtils.currentOrgIds(authentication));
    SignatureFileDownload download =
        signatureService.downloadSignatureFile(caseEntity, signatureId);
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .header(
            HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + download.fileName() + "\"")
        .contentLength(download.content().length)
        .body(new ByteArrayResource(download.content()));
  }

  @PostMapping("/{signatureId}/cancel")
  public ResponseEntity<SignatureRequestResponse> cancel(
      @PathVariable UUID caseId, @PathVariable UUID signatureId, Authentication authentication) {
    UUID lawyerId = SecurityUtils.currentUserId(authentication);
    return ResponseEntity.ok(signatureService.cancel(caseId, signatureId, lawyerId));
  }
}
