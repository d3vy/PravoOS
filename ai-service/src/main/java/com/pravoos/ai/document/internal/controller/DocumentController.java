package com.pravoos.ai.document.internal.controller;

import com.pravoos.ai.document.api.DocumentContent;
import com.pravoos.ai.document.api.DocumentResponse;
import com.pravoos.ai.document.api.DocumentUploadResponse;
import com.pravoos.ai.document.internal.service.DocumentService;
import com.pravoos.ai.shared.model.enums.AuditAction;
import com.pravoos.ai.shared.service.AccessAuditService;
import com.pravoos.ai.shared.util.PagedResponse;
import com.pravoos.ai.shared.util.SecureFileHeaders;
import com.pravoos.common.web.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/ai/documents")
public class DocumentController {

    private final DocumentService documentService;
    private final AccessAuditService accessAuditService;

    public DocumentController(DocumentService documentService, AccessAuditService accessAuditService) {
        this.documentService = documentService;
        this.accessAuditService = accessAuditService;
    }

    @PostMapping
    public ResponseEntity<DocumentUploadResponse> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "title", required = false) String title,
            Authentication authentication) {
        UUID adminId = SecurityUtils.currentUserId(authentication);
        return ResponseEntity.status(HttpStatus.CREATED).body(documentService.upload(file, title, adminId));
    }

    @GetMapping
    public ResponseEntity<List<DocumentResponse>> findAll(@RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "20") int size) {
        return PagedResponse.of(documentService.findAll(page, size));
    }

    @GetMapping("/{id}/content")
    public ResponseEntity<Resource> content(@PathVariable UUID id,
                                            Authentication authentication,
                                            HttpServletRequest request) {
        DocumentContent document = documentService.loadContent(id);
        accessAuditService.record(authentication, AuditAction.DOCUMENT_DOWNLOAD, id, request);

        boolean inline = !"docx".equals(document.fileType());
        ContentDisposition disposition = (inline
                ? ContentDisposition.inline()
                : ContentDisposition.attachment())
                .filename(document.fileName(), StandardCharsets.UTF_8)
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(disposition);
        SecureFileHeaders.apply(headers);

        return ResponseEntity.ok()
                .headers(headers)
                .contentType(resolveMediaType(document.fileType()))
                .contentLength(document.contentLength())
                .body(document.resource());
    }

    private MediaType resolveMediaType(String fileType) {
        return switch (fileType) {
            case "pdf" -> MediaType.APPLICATION_PDF;
            case "txt" -> new MediaType("text", "plain", StandardCharsets.UTF_8);
            case "docx" -> MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            default -> MediaType.APPLICATION_OCTET_STREAM;
        };
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        documentService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
