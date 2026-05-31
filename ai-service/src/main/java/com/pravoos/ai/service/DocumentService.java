package com.pravoos.ai.service;

import com.pravoos.ai.config.DocumentProperties;
import com.pravoos.ai.event.DocumentCreatedSpringEvent;
import com.pravoos.ai.exception.DocumentNotFoundException;
import com.pravoos.ai.exception.DocumentProcessingException;
import com.pravoos.ai.model.dto.DocumentResponse;
import com.pravoos.ai.model.dto.DocumentUploadResponse;
import com.pravoos.ai.model.entity.Document;
import com.pravoos.ai.model.entity.DocumentChunk;
import com.pravoos.ai.model.enums.DocumentStatus;
import com.pravoos.ai.pipeline.ChunkData;
import com.pravoos.ai.repository.jpa.DocumentChunkRepository;
import com.pravoos.ai.repository.jpa.DocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final DocumentProperties documentProperties;

    public DocumentService(DocumentRepository documentRepository,
                           DocumentChunkRepository documentChunkRepository,
                           ApplicationEventPublisher eventPublisher,
                           DocumentProperties documentProperties) {
        this.documentRepository = documentRepository;
        this.documentChunkRepository = documentChunkRepository;
        this.eventPublisher = eventPublisher;
        this.documentProperties = documentProperties;
    }

    @Transactional
    public DocumentUploadResponse upload(MultipartFile file, String title, UUID uploadedBy) {
        if (file == null || file.isEmpty()) {
            log.warn("Document upload rejected: empty file from {}", uploadedBy);
            throw new DocumentProcessingException("Uploaded file is empty");
        }
        String originalName = file.getOriginalFilename();
        String fileType = extractFileType(originalName);

        Document document = new Document();
        document.setTitle(resolveTitle(title, originalName));
        document.setFileName(originalName);
        document.setFileType(fileType);
        document.setUploadedBy(uploadedBy);

        Document saved = documentRepository.save(document);

        Path filePath = storeFile(file, saved.getId(), fileType);
        saved.setFilePath(filePath.toString());
        documentRepository.save(saved);

        eventPublisher.publishEvent(new DocumentCreatedSpringEvent(saved.getId()));

        log.info("Document uploaded: '{}' ({}) by {}", saved.getTitle(), originalName, uploadedBy);
        return new DocumentUploadResponse(saved.getId(), saved.getTitle(), saved.getFileName(), saved.getStatus());
    }

    @Transactional
    public void completeProcessing(UUID documentId, List<ChunkData> chunkData) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException(documentId));

        List<DocumentChunk> chunks = chunkData.stream().map(data -> {
            DocumentChunk chunk = new DocumentChunk();
            chunk.setDocument(document);
            chunk.setContent(data.content());
            chunk.setChunkIndex(data.index());
            chunk.setEmbedding(data.embedding());
            return chunk;
        }).toList();

        documentChunkRepository.saveAll(chunks);
        document.setStatus(DocumentStatus.READY);
        documentRepository.save(document);
        log.info("Document {} marked READY with {} chunk(s)", documentId, chunks.size());
    }

    @Transactional
    public void markFailed(UUID documentId) {
        documentRepository.findById(documentId).ifPresent(document -> {
            document.setStatus(DocumentStatus.FAILED);
            documentRepository.save(document);
            log.warn("Document {} marked FAILED", documentId);
        });
    }

    @Transactional
    public void delete(UUID documentId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException(documentId));

        String filePath = document.getFilePath();
        documentChunkRepository.deleteByDocumentId(documentId);
        documentRepository.delete(document);

        if (filePath != null) {
            deleteFile(Paths.get(filePath));
        }

        log.info("Document deleted: {}", documentId);
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> findAll() {
        return documentRepository.findAllByOrderByUploadedAtDesc()
                .stream()
                .map(this::toDocumentResponse)
                .toList();
    }

    private Path storeFile(MultipartFile file, UUID documentId, String fileType) {
        try {
            Path dir = Paths.get(documentProperties.storagePath(), documentId.toString());
            Files.createDirectories(dir);
            Path filePath = dir.resolve("document." + fileType);
            file.transferTo(filePath);
            return filePath;
        } catch (IOException e) {
            throw new DocumentProcessingException("Failed to store file: " + e.getMessage());
        }
    }

    private void deleteFile(Path filePath) {
        try {
            Files.deleteIfExists(filePath);
            Files.deleteIfExists(filePath.getParent());
        } catch (IOException e) {
            log.warn("Failed to delete file: {}", filePath, e);
        }
    }

    private String extractFileType(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            throw new DocumentProcessingException("Invalid file name: " + fileName);
        }
        String ext = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
        if (!ext.equals("pdf") && !ext.equals("docx")) {
            throw new DocumentProcessingException("Unsupported file type: " + ext + ". Allowed: pdf, docx");
        }
        return ext;
    }

    private String resolveTitle(String title, String fileName) {
        if (title != null && !title.isBlank()) {
            return title.trim();
        }
        return stripExtension(fileName);
    }

    private String stripExtension(String fileName) {
        if (fileName == null) return "Unnamed";
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    private DocumentResponse toDocumentResponse(Document document) {
        return new DocumentResponse(
                document.getId(),
                document.getTitle(),
                document.getFileName(),
                document.getFileType(),
                document.getStatus(),
                document.getUploadedAt()
        );
    }
}
