package com.pravoos.ai.controller;

import com.pravoos.ai.model.dto.DocumentContent;
import com.pravoos.ai.model.dto.DocumentResponse;
import com.pravoos.ai.model.dto.DocumentUploadResponse;
import com.pravoos.common.web.SecurityUtils;
import com.pravoos.ai.service.DocumentService;
import org.springframework.core.io.Resource;
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
@RequestMapping("/api/ai/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
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
    public ResponseEntity<List<DocumentResponse>> findAll() {
        return ResponseEntity.ok(documentService.findAll());
    }

    @GetMapping("/{id}/content")
    public ResponseEntity<Resource> content(@PathVariable UUID id) {
        DocumentContent document = documentService.loadContent(id);

        boolean inline = !"docx".equals(document.fileType());
        ContentDisposition disposition = (inline
                ? ContentDisposition.inline()
                : ContentDisposition.attachment())
                .filename(document.fileName(), StandardCharsets.UTF_8)
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(disposition);

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
