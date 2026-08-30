package com.pravoos.ai.practice.internal.controller;

import com.pravoos.ai.practice.internal.dto.DeclineSignatureRequest;
import com.pravoos.ai.practice.internal.dto.SignDocumentRequest;
import com.pravoos.ai.practice.internal.dto.SignatureRequestResponse;
import com.pravoos.ai.practice.internal.dto.SignerContext;
import com.pravoos.ai.practice.internal.service.PortalSignatureService;
import com.pravoos.ai.shared.exception.InvalidSignatureFileException;
import com.pravoos.ai.shared.security.CallerContext;
import com.pravoos.ai.shared.util.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/ai/portal")
public class PortalSignatureController {

  private final PortalSignatureService portalSignatureService;

  public PortalSignatureController(PortalSignatureService portalSignatureService) {
    this.portalSignatureService = portalSignatureService;
  }

  @GetMapping("/signatures")
  public ResponseEntity<List<SignatureRequestResponse>> listPending(CallerContext caller) {
    return ResponseEntity.ok(portalSignatureService.listPending(caller.clientIds()));
  }

  @GetMapping("/cases/{caseId}/signatures")
  public ResponseEntity<List<SignatureRequestResponse>> listByCase(
      @PathVariable UUID caseId, CallerContext caller) {
    return ResponseEntity.ok(portalSignatureService.listByCase(caseId, caller.clientIds()));
  }

  @PostMapping("/signatures/{signatureId}/sign")
  public ResponseEntity<SignatureRequestResponse> sign(
      @PathVariable UUID signatureId,
      @Valid @RequestBody SignDocumentRequest request,
      CallerContext caller,
      HttpServletRequest httpRequest) {
    return ResponseEntity.ok(
        portalSignatureService.sign(
            signatureId, request, caller.clientIds(), signerContext(caller, httpRequest)));
  }

  @PostMapping("/signatures/{signatureId}/sign-cms")
  public ResponseEntity<SignatureRequestResponse> signWithCms(
      @PathVariable UUID signatureId,
      @RequestParam("file") MultipartFile file,
      CallerContext caller,
      HttpServletRequest httpRequest) {
    return ResponseEntity.ok(
        portalSignatureService.signWithCms(
            signatureId,
            readBytes(file),
            file.getOriginalFilename(),
            caller.clientIds(),
            signerContext(caller, httpRequest)));
  }

  @GetMapping("/signatures/{signatureId}/protocol")
  public ResponseEntity<Resource> exportProtocol(
      @PathVariable UUID signatureId, CallerContext caller) {
    byte[] protocol = portalSignatureService.exportProtocol(signatureId, caller.clientIds());
    return protocolResponse(signatureId, protocol);
  }

  @PostMapping("/signatures/{signatureId}/decline")
  public ResponseEntity<SignatureRequestResponse> decline(
      @PathVariable UUID signatureId,
      @Valid @RequestBody DeclineSignatureRequest request,
      CallerContext caller,
      HttpServletRequest httpRequest) {
    return ResponseEntity.ok(
        portalSignatureService.decline(
            signatureId, request.reason(), caller.clientIds(), signerContext(caller, httpRequest)));
  }

  static ResponseEntity<Resource> protocolResponse(UUID signatureId, byte[] protocol) {
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_PDF)
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"signature-protocol-" + signatureId + ".pdf\"")
        .contentLength(protocol.length)
        .body(new ByteArrayResource(protocol));
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
