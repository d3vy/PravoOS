package com.pravoos.ai.practice.internal.controller;

import com.pravoos.ai.practice.internal.dto.DeclineSignatureRequest;
import com.pravoos.ai.practice.internal.dto.SignDocumentRequest;
import com.pravoos.ai.practice.internal.dto.SignatureRequestResponse;
import com.pravoos.ai.practice.internal.dto.SignerContext;
import com.pravoos.ai.practice.internal.service.PortalSignatureService;
import com.pravoos.ai.shared.exception.InvalidSignatureFileException;
import com.pravoos.ai.shared.util.ClientIpResolver;
import com.pravoos.common.web.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/ai/portal")
public class PortalSignatureController {

    private final PortalSignatureService portalSignatureService;

    public PortalSignatureController(PortalSignatureService portalSignatureService) {
        this.portalSignatureService = portalSignatureService;
    }

    @GetMapping("/signatures")
    public ResponseEntity<List<SignatureRequestResponse>> listPending(Authentication authentication) {
        return ResponseEntity.ok(portalSignatureService.listPending(
                SecurityUtils.currentClientIds(authentication)));
    }

    @GetMapping("/cases/{caseId}/signatures")
    public ResponseEntity<List<SignatureRequestResponse>> listByCase(@PathVariable UUID caseId,
                                                                    Authentication authentication) {
        return ResponseEntity.ok(portalSignatureService.listByCase(caseId,
                SecurityUtils.currentClientIds(authentication)));
    }

    @PostMapping("/signatures/{signatureId}/sign")
    public ResponseEntity<SignatureRequestResponse> sign(@PathVariable UUID signatureId,
                                                        @Valid @RequestBody SignDocumentRequest request,
                                                        Authentication authentication,
                                                        HttpServletRequest httpRequest) {
        return ResponseEntity.ok(portalSignatureService.sign(signatureId, request,
                SecurityUtils.currentClientIds(authentication), signerContext(authentication, httpRequest)));
    }

    @PostMapping("/signatures/{signatureId}/sign-cms")
    public ResponseEntity<SignatureRequestResponse> signWithCms(@PathVariable UUID signatureId,
                                                               @RequestParam("file") MultipartFile file,
                                                               Authentication authentication,
                                                               HttpServletRequest httpRequest) {
        return ResponseEntity.ok(portalSignatureService.signWithCms(signatureId, readBytes(file),
                file.getOriginalFilename(), SecurityUtils.currentClientIds(authentication),
                signerContext(authentication, httpRequest)));
    }

    @GetMapping("/signatures/{signatureId}/protocol")
    public ResponseEntity<Resource> exportProtocol(@PathVariable UUID signatureId,
                                                   Authentication authentication) {
        byte[] protocol = portalSignatureService.exportProtocol(signatureId,
                SecurityUtils.currentClientIds(authentication));
        return protocolResponse(signatureId, protocol);
    }

    @PostMapping("/signatures/{signatureId}/decline")
    public ResponseEntity<SignatureRequestResponse> decline(@PathVariable UUID signatureId,
                                                           @Valid @RequestBody DeclineSignatureRequest request,
                                                           Authentication authentication,
                                                           HttpServletRequest httpRequest) {
        return ResponseEntity.ok(portalSignatureService.decline(signatureId, request.reason(),
                SecurityUtils.currentClientIds(authentication), signerContext(authentication, httpRequest)));
    }

    static ResponseEntity<Resource> protocolResponse(UUID signatureId, byte[] protocol) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
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

    private SignerContext signerContext(Authentication authentication, HttpServletRequest httpRequest) {
        return new SignerContext(
                SecurityUtils.currentUserId(authentication),
                ClientIpResolver.resolve(httpRequest),
                httpRequest.getHeader(HttpHeaders.USER_AGENT));
    }
}
