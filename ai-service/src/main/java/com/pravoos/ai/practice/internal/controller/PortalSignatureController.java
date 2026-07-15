package com.pravoos.ai.practice.internal.controller;

import com.pravoos.ai.practice.internal.dto.DeclineSignatureRequest;
import com.pravoos.ai.practice.internal.dto.SignDocumentRequest;
import com.pravoos.ai.practice.internal.dto.SignatureRequestResponse;
import com.pravoos.ai.practice.internal.dto.SignerContext;
import com.pravoos.ai.practice.internal.service.PortalSignatureService;
import com.pravoos.ai.shared.util.ClientIpResolver;
import com.pravoos.common.web.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

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

    @PostMapping("/signatures/{signatureId}/decline")
    public ResponseEntity<SignatureRequestResponse> decline(@PathVariable UUID signatureId,
                                                           @Valid @RequestBody DeclineSignatureRequest request,
                                                           Authentication authentication,
                                                           HttpServletRequest httpRequest) {
        return ResponseEntity.ok(portalSignatureService.decline(signatureId, request.reason(),
                SecurityUtils.currentClientIds(authentication), signerContext(authentication, httpRequest)));
    }

    private SignerContext signerContext(Authentication authentication, HttpServletRequest httpRequest) {
        return new SignerContext(
                SecurityUtils.currentUserId(authentication),
                ClientIpResolver.resolve(httpRequest),
                httpRequest.getHeader(HttpHeaders.USER_AGENT));
    }
}
