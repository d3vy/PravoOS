package com.pravoos.ai.document.internal.service;

import com.pravoos.ai.document.api.DocumentContent;
import com.pravoos.ai.document.api.DocumentResponse;
import com.pravoos.ai.document.api.DocumentUploadResponse;
import com.pravoos.ai.document.internal.dto.LegislationResponse;
import com.pravoos.ai.document.internal.event.DocumentCreatedSpringEvent;
import com.pravoos.ai.document.internal.model.entity.Document;
import com.pravoos.ai.document.internal.model.entity.DocumentChunk;
import com.pravoos.ai.document.internal.pipeline.ChunkData;
import com.pravoos.ai.document.internal.repository.jpa.DocumentChunkRepository;
import com.pravoos.ai.document.internal.repository.jpa.DocumentRepository;
import com.pravoos.ai.shared.config.DocumentProperties;
import com.pravoos.ai.shared.exception.DocumentNotFoundException;
import com.pravoos.ai.shared.exception.DocumentProcessingException;
import com.pravoos.ai.shared.exception.StorageQuotaExceededException;
import com.pravoos.ai.shared.model.enums.DocumentKind;
import com.pravoos.ai.shared.model.enums.DocumentStatus;
import com.pravoos.ai.shared.util.PageRequests;
import com.pravoos.ai.shared.util.Sha256;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final DocumentProperties documentProperties;
    private final FileCryptoService fileCryptoService;
    private final MalwareScanClient malwareScanClient;
    private final UploadRateLimiter uploadRateLimiter;
    private final Counter processingFailedCounter;

    public DocumentService(DocumentRepository documentRepository,
                           DocumentChunkRepository documentChunkRepository,
                           ApplicationEventPublisher eventPublisher,
                           DocumentProperties documentProperties,
                           FileCryptoService fileCryptoService,
                           MalwareScanClient malwareScanClient,
                           UploadRateLimiter uploadRateLimiter,
                           MeterRegistry meterRegistry) {
        this.documentRepository = documentRepository;
        this.documentChunkRepository = documentChunkRepository;
        this.eventPublisher = eventPublisher;
        this.documentProperties = documentProperties;
        this.fileCryptoService = fileCryptoService;
        this.malwareScanClient = malwareScanClient;
        this.uploadRateLimiter = uploadRateLimiter;
        this.processingFailedCounter = Counter.builder("pravoos.document.processing")
                .description("Document embedding-pipeline outcomes")
                .tag("result", "failed")
                .register(meterRegistry);
    }

    @Transactional
    public DocumentUploadResponse upload(MultipartFile file, String title, UUID uploadedBy) {
        return upload(file, title, uploadedBy, null);
    }

    @Transactional
    public DocumentUploadResponse upload(MultipartFile file, String title, UUID uploadedBy, UUID caseId) {
        return upload(file, title, uploadedBy, caseId, false);
    }

    @Transactional
    public DocumentUploadResponse upload(MultipartFile file, String title, UUID uploadedBy, UUID caseId,
                                         boolean visibleToClient) {
        if (file == null || file.isEmpty()) {
            log.warn("Document upload rejected: empty file from {}", uploadedBy);
            throw new DocumentProcessingException("Uploaded file is empty");
        }
        uploadRateLimiter.assertWithinLimit(uploadedBy);
        if (caseId != null) {
            long existing = documentRepository.countByCaseId(caseId);
            if (existing >= documentProperties.maxPerCase()) {
                log.warn("Document upload rejected: case {} reached the limit of {} documents",
                        caseId, documentProperties.maxPerCase());
                throw new DocumentProcessingException(
                        "Достигнут лимит документов на дело (" + documentProperties.maxPerCase() + ")");
            }
        }

        String originalName = file.getOriginalFilename();
        String fileType = extractFileType(originalName);
        byte[] content = validateAndScan(file, uploadedBy, originalName, fileType);

        Document document = new Document();
        document.setTitle(resolveTitle(title, originalName));
        document.setUploadedBy(uploadedBy);
        document.setCaseId(caseId);
        document.setVisibleToClient(visibleToClient);

        return persistAndEmbed(content, originalName, fileType, document);
    }

    @Transactional
    public DocumentUploadResponse uploadChatAttachment(MultipartFile file, String title, UUID lawyerId) {
        if (file == null || file.isEmpty()) {
            log.warn("Chat attachment upload rejected: empty file from {}", lawyerId);
            throw new DocumentProcessingException("Uploaded file is empty");
        }
        uploadRateLimiter.assertWithinLimit(lawyerId);

        String originalName = file.getOriginalFilename();
        String fileType = extractFileType(originalName);
        byte[] content = validateAndScan(file, lawyerId, originalName, fileType);

        Document document = new Document();
        document.setTitle(resolveTitle(title, originalName));
        document.setUploadedBy(lawyerId);
        document.setCaseId(null);
        document.setVisibleToClient(false);
        document.setDocumentKind(DocumentKind.CHAT_ATTACHMENT);

        return persistAndEmbed(content, originalName, fileType, document);
    }

    @Transactional
    public DocumentUploadResponse uploadLegislation(MultipartFile file, String actCanonical, String articleNumber,
                                                    LocalDate editionDate, String title, UUID uploadedBy) {
        if (file == null || file.isEmpty()) {
            log.warn("Legislation upload rejected: empty file from {}", uploadedBy);
            throw new DocumentProcessingException("Uploaded file is empty");
        }
        uploadRateLimiter.assertWithinLimit(uploadedBy);

        String act = requireText(actCanonical, "act");
        String article = requireText(articleNumber, "article");
        if (editionDate == null) {
            throw new DocumentProcessingException("Дата редакции обязательна для НПА");
        }

        String originalName = file.getOriginalFilename();
        String fileType = extractFileType(originalName);
        byte[] content = validateAndScan(file, uploadedBy, originalName, fileType);

        documentRepository.findByDocumentKindAndActCanonicalAndArticleNumberAndSupersededFalse(
                        DocumentKind.LEGISLATION, act, article)
                .ifPresent(current -> {
                    current.setSuperseded(true);
                    documentRepository.saveAndFlush(current);
                    log.info("Legislation superseded: {} {} (doc {})", act, article, current.getId());
                });

        Document document = new Document();
        document.setTitle(resolveTitle(title, "ст. " + article + " " + act));
        document.setUploadedBy(uploadedBy);
        document.setDocumentKind(DocumentKind.LEGISLATION);
        document.setActCanonical(act);
        document.setArticleNumber(article);
        document.setEditionDate(editionDate);

        DocumentUploadResponse response = persistAndEmbed(content, originalName, fileType, document);
        log.info("Legislation uploaded: {} {} ред. от {} by {}", act, article, editionDate, uploadedBy);
        return response;
    }

    private byte[] validateAndScan(MultipartFile file, UUID uploadedBy, String originalName, String fileType) {
        byte[] content = readBytes(file);
        enforceStorageQuota(uploadedBy, content.length);
        validateContentMatchesType(content, fileType);
        malwareScanClient.scan(content, originalName);
        return content;
    }

    private DocumentUploadResponse persistAndEmbed(byte[] content, String originalName, String fileType,
                                                   Document document) {
        Path filePath = storeFile(content, UUID.randomUUID().toString(), fileType);
        document.setFileName(originalName);
        document.setFileType(fileType);
        document.setFilePath(filePath.toString());
        document.setSizeBytes(content.length);

        Document saved = documentRepository.save(document);
        eventPublisher.publishEvent(new DocumentCreatedSpringEvent(saved.getId()));

        log.info("Document uploaded: '{}' ({}) by {}", saved.getTitle(), originalName, document.getUploadedBy());
        return new DocumentUploadResponse(saved.getId(), saved.getTitle(), saved.getFileName(), saved.getStatus());
    }

    private String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new DocumentProcessingException("Поле '" + field + "' обязательно для НПА");
        }
        return value.trim();
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
            processingFailedCounter.increment();
            log.warn("Document {} marked FAILED", documentId);
        });
    }

    @Transactional
    public void delete(UUID documentId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException(documentId));
        requireKnowledgeBaseDocument(document);

        String filePath = document.getFilePath();
        documentChunkRepository.deleteByDocumentId(documentId);
        documentRepository.delete(document);

        if (filePath != null) {
            deleteFile(Paths.get(filePath));
        }

        log.info("Document deleted: {}", documentId);
    }

    @Transactional
    public void deleteByCase(UUID caseId) {
        List<Document> documents = documentRepository.findByCaseIdOrderByUploadedAtDesc(caseId);
        for (Document document : documents) {
            String filePath = document.getFilePath();
            documentChunkRepository.deleteByDocumentId(document.getId());
            documentRepository.delete(document);
            if (filePath != null) {
                deleteFile(Paths.get(filePath));
            }
        }
        log.info("Deleted {} document(s) of case {}", documents.size(), caseId);
    }

    @Transactional(readOnly = true)
    public Page<DocumentResponse> findAll(int page, int size) {
        return documentRepository
                .findByCaseIdIsNullAndDocumentKindNotOrderByUploadedAtDesc(
                        DocumentKind.CHAT_ATTACHMENT, PageRequests.of(page, size))
                .map(this::toDocumentResponse);
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> findChatAttachments(UUID lawyerId) {
        return documentRepository
                .findByUploadedByAndDocumentKindOrderByUploadedAtDesc(lawyerId, DocumentKind.CHAT_ATTACHMENT)
                .stream()
                .map(this::toDocumentResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<LegislationResponse> findLegislation(int page, int size) {
        return documentRepository
                .findByDocumentKindAndSupersededFalseOrderByEditionDateDesc(
                        DocumentKind.LEGISLATION, PageRequests.of(page, size))
                .map(this::toLegislationResponse);
    }

    @Transactional(readOnly = true)
    public DocumentContent loadContent(UUID documentId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException(documentId));
        requireKnowledgeBaseDocument(document);
        return buildContent(document);
    }

    @Transactional(readOnly = true)
    public DocumentContent loadClientContent(UUID documentId, UUID caseId) {
        Document document = requireClientVisibleDocument(documentId, caseId);
        return buildContent(document);
    }

    @Transactional(readOnly = true)
    public String contentSha256(UUID documentId, UUID caseId) {
        Document document = requireClientVisibleDocument(documentId, caseId);
        Path path = Paths.get(document.getFilePath());
        if (!Files.isReadable(path)) {
            log.warn("Document {} has missing file on disk: {}", document.getId(), path);
            throw new DocumentNotFoundException(document.getId());
        }
        return Sha256.hex(fileCryptoService.decryptFile(path));
    }

    private Document requireClientVisibleDocument(UUID documentId, UUID caseId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException(documentId));
        if (!caseId.equals(document.getCaseId()) || !document.isVisibleToClient()) {
            log.warn("Client document access denied: doc {} (case {}, visible {}) for requested case {}",
                    documentId, document.getCaseId(), document.isVisibleToClient(), caseId);
            throw new DocumentNotFoundException(documentId);
        }
        return document;
    }

    @Transactional
    public DocumentResponse setClientVisibility(UUID documentId, UUID caseId, boolean visibleToClient) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException(documentId));
        if (!caseId.equals(document.getCaseId())) {
            log.warn("Visibility change denied: doc {} does not belong to case {}", documentId, caseId);
            throw new DocumentNotFoundException(documentId);
        }
        document.setVisibleToClient(visibleToClient);
        Document saved = documentRepository.save(document);
        log.info("Document {} client-visibility set to {}", documentId, visibleToClient);
        return toDocumentResponse(saved);
    }

    private DocumentContent buildContent(Document document) {
        Path path = Paths.get(document.getFilePath());
        if (!Files.isReadable(path)) {
            log.warn("Document {} has missing file on disk: {}", document.getId(), path);
            throw new DocumentNotFoundException(document.getId());
        }
        byte[] content = fileCryptoService.decryptFile(path);
        return new DocumentContent(
                new ByteArrayResource(content),
                document.getFileName(),
                document.getFileType(),
                content.length);
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> findByCase(UUID caseId) {
        return documentRepository.findByCaseIdOrderByUploadedAtDesc(caseId)
                .stream()
                .map(this::toDocumentResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> findClientVisibleByCase(UUID caseId) {
        return documentRepository.findByCaseIdAndVisibleToClientTrueOrderByUploadedAtDesc(caseId)
                .stream()
                .map(this::toDocumentResponse)
                .toList();
    }

    private void enforceStorageQuota(UUID uploadedBy, long incomingBytes) {
        int maxDocuments = documentProperties.maxPerLawyer();
        if (maxDocuments > 0 && documentRepository.countByUploadedBy(uploadedBy) >= maxDocuments) {
            log.warn("Document upload rejected: user {} reached the limit of {} documents",
                    uploadedBy, maxDocuments);
            throw new StorageQuotaExceededException(
                    "Достигнут лимит числа документов (" + maxDocuments + ")");
        }
        long maxBytes = documentProperties.maxTotalBytesPerLawyer();
        if (maxBytes > 0) {
            long used = documentRepository.sumSizeBytesByUploadedBy(uploadedBy);
            if (used + incomingBytes > maxBytes) {
                log.warn("Document upload rejected: user {} would exceed storage quota ({}+{} > {} bytes)",
                        uploadedBy, used, incomingBytes, maxBytes);
                throw new StorageQuotaExceededException(
                        "Достигнут лимит объёма хранилища (" + (maxBytes / (1024 * 1024)) + " МБ)");
            }
        }
    }

    private void requireKnowledgeBaseDocument(Document document) {
        if (document.getCaseId() != null) {
            log.warn("Rejected knowledge-base operation on case-bound document {}", document.getId());
            throw new DocumentNotFoundException(document.getId());
        }
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new DocumentProcessingException("Failed to read uploaded file: " + e.getMessage());
        }
    }

    private Path storeFile(byte[] content, String storageKey, String fileType) {
        try {
            Path dir = Paths.get(documentProperties.storagePath(), storageKey);
            Files.createDirectories(dir);
            Path filePath = dir.resolve("document." + fileType);
            fileCryptoService.encryptToFile(content, filePath);
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

    private static final byte[] PDF_SIGNATURE = {0x25, 0x50, 0x44, 0x46};
    private static final byte[] ZIP_SIGNATURE = {0x50, 0x4B, 0x03, 0x04};

    private static final String DOCX_CONTENT_TYPES_ENTRY = "[Content_Types].xml";

    private void validateContentMatchesType(byte[] content, String fileType) {
        boolean matches = switch (fileType) {
            case "pdf" -> startsWith(content, PDF_SIGNATURE);
            case "docx" -> isValidDocx(content);
            case "txt" -> true;
            default -> false;
        };
        if (!matches) {
            throw new DocumentProcessingException("File content does not match its extension ." + fileType);
        }
    }

    private boolean isValidDocx(byte[] content) {
        if (!startsWith(content, ZIP_SIGNATURE)) {
            return false;
        }
        try (var zipInputStream = new java.util.zip.ZipInputStream(new ByteArrayInputStream(content))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (DOCX_CONTENT_TYPES_ENTRY.equals(entry.getName())) {
                    return true;
                }
            }
            return false;
        } catch (IOException e) {
            throw new DocumentProcessingException("Failed to read uploaded file: " + e.getMessage());
        }
    }

    private boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private String extractFileType(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            throw new DocumentProcessingException("Invalid file name: " + fileName);
        }
        String ext = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
        if (!ext.equals("pdf") && !ext.equals("docx") && !ext.equals("txt")) {
            throw new DocumentProcessingException("Unsupported file type: " + ext + ". Allowed: pdf, docx, txt");
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

    private LegislationResponse toLegislationResponse(Document document) {
        return new LegislationResponse(
                document.getId(),
                document.getTitle(),
                document.getActCanonical(),
                document.getArticleNumber(),
                document.getEditionDate(),
                document.getStatus(),
                document.isSuperseded()
        );
    }

    private DocumentResponse toDocumentResponse(Document document) {
        return new DocumentResponse(
                document.getId(),
                document.getTitle(),
                document.getFileName(),
                document.getFileType(),
                document.getStatus(),
                document.getUploadedAt(),
                document.isVisibleToClient()
        );
    }
}
