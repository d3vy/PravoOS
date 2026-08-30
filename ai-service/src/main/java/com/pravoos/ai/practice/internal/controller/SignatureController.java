package com.pravoos.ai.practice.internal.controller;

import com.pravoos.ai.practice.internal.dto.CreateSignatureRequestDto;
import com.pravoos.ai.practice.internal.dto.DeclineSignatureRequest;
import com.pravoos.ai.practice.internal.dto.SignDocumentRequest;
import com.pravoos.ai.practice.internal.dto.SignatureRequestResponse;
import com.pravoos.ai.practice.internal.dto.SignerContext;
import com.pravoos.ai.practice.internal.model.entity.Case;
import com.pravoos.ai.practice.internal.service.CaseService;
import com.pravoos.ai.practice.internal.service.SignatureService;
import com.pravoos.ai.practice.internal.service.SignatureService.SignatureFileDownload;
import com.pravoos.ai.shared.exception.InvalidSignatureFileException;
import com.pravoos.ai.shared.security.CallerContext;
import com.pravoos.ai.shared.util.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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
      CallerContext caller) {
    UUID lawyerId = caller.userId();
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(signatureService.create(caseId, request, lawyerId));
  }

  @GetMapping
  public ResponseEntity<List<SignatureRequestResponse>> list(
      @PathVariable UUID caseId, CallerContext caller) {
    UUID lawyerId = caller.userId();
    return ResponseEntity.ok(signatureService.findByCase(caseId, lawyerId, caller.orgIds()));
  }

  @GetMapping("/{signatureId}/protocol")
  public ResponseEntity<Resource> exportProtocol(
      @PathVariable UUID caseId, @PathVariable UUID signatureId, CallerContext caller) {
    Case caseEntity = visibleCase(caseId, caller);
    byte[] protocol = signatureService.exportProtocol(caseEntity, signatureId);
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_PDF)
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment()
                .filename("signature-protocol-" + signatureId + ".pdf", StandardCharsets.UTF_8)
                .build()
                .toString())
        .contentLength(protocol.length)
        .body(new ByteArrayResource(protocol));
  }

  @GetMapping("/{signatureId}/signature-file")
  public ResponseEntity<Resource> downloadSignatureFile(
      @PathVariable UUID caseId, @PathVariable UUID signatureId, CallerContext caller) {
    Case caseEntity = visibleCase(caseId, caller);
    SignatureFileDownload download =
        signatureService.downloadSignatureFile(caseEntity, signatureId);
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment()
                .filename(download.fileName(), StandardCharsets.UTF_8)
                .build()
                .toString())
        .contentLength(download.content().length)
        .body(new ByteArrayResource(download.content()));
  }

  @PostMapping("/{signatureId}/sign")
  public ResponseEntity<SignatureRequestResponse> sign(
      @PathVariable UUID caseId,
      @PathVariable UUID signatureId,
      @Valid @RequestBody SignDocumentRequest request,
      CallerContext caller,
      HttpServletRequest httpRequest) {
    Case caseEntity = visibleCase(caseId, caller);
    return ResponseEntity.ok(
        signatureService.signAsLawyer(
            caseEntity, signatureId, request, signerContext(caller, httpRequest)));
  }

  @PostMapping("/{signatureId}/sign-cms")
  public ResponseEntity<SignatureRequestResponse> signWithCms(
      @PathVariable UUID caseId,
      @PathVariable UUID signatureId,
      @RequestParam("file") MultipartFile file,
      CallerContext caller,
      HttpServletRequest httpRequest) {
    Case caseEntity = visibleCase(caseId, caller);
    return ResponseEntity.ok(
        signatureService.signWithCmsAsLawyer(
            caseEntity,
            signatureId,
            readBytes(file),
            file.getOriginalFilename(),
            signerContext(caller, httpRequest)));
  }

  @PostMapping("/{signatureId}/decline")
  public ResponseEntity<SignatureRequestResponse> decline(
      @PathVariable UUID caseId,
      @PathVariable UUID signatureId,
      @Valid @RequestBody DeclineSignatureRequest request,
      CallerContext caller,
      HttpServletRequest httpRequest) {
    Case caseEntity = visibleCase(caseId, caller);
    return ResponseEntity.ok(
        signatureService.declineAsLawyer(
            caseEntity, signatureId, request.reason(), signerContext(caller, httpRequest)));
  }

  @PostMapping("/{signatureId}/cancel")
  public ResponseEntity<SignatureRequestResponse> cancel(
      @PathVariable UUID caseId, @PathVariable UUID signatureId, CallerContext caller) {
    UUID lawyerId = caller.userId();
    return ResponseEntity.ok(signatureService.cancel(caseId, signatureId, lawyerId));
  }

  private Case visibleCase(UUID caseId, CallerContext caller) {
    return caseService.requireVisibleCase(caseId, caller.userId(), caller.orgIds());
  }

  private byte[] readBytes(MultipartFile file) {
    try {
      return file.getBytes();
    } catch (IOException ex) {
      throw new InvalidSignatureFileException("не удалось прочитать загруженный файл");
    }
  }

  private SignerContext signerContext(CallerContext caller, HttpServletRequest httpRequest) {
    return new SignerContext(
        caller.userId(),
        ClientIpResolver.resolve(httpRequest),
        httpRequest.getHeader(HttpHeaders.USER_AGENT));
  }
}
