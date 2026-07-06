package com.pravoos.ai.core.internal;

import com.pravoos.ai.core.api.DocumentAccess;
import com.pravoos.ai.core.api.DocumentRef;
import com.pravoos.ai.core.internal.model.entity.Document;
import com.pravoos.ai.core.internal.pipeline.DocumentParser;
import com.pravoos.ai.core.internal.repository.jpa.DocumentChunkRepository;
import com.pravoos.ai.core.internal.repository.jpa.DocumentRepository;
import com.pravoos.ai.core.internal.service.FileCryptoService;
import com.pravoos.ai.shared.exception.DocumentNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

@Service
class DocumentAccessImpl implements DocumentAccess {

    private static final Logger log = LoggerFactory.getLogger(DocumentAccessImpl.class);

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final FileCryptoService fileCryptoService;
    private final DocumentParser documentParser;

    DocumentAccessImpl(DocumentRepository documentRepository,
                       DocumentChunkRepository documentChunkRepository,
                       FileCryptoService fileCryptoService,
                       DocumentParser documentParser) {
        this.documentRepository = documentRepository;
        this.documentChunkRepository = documentChunkRepository;
        this.fileCryptoService = fileCryptoService;
        this.documentParser = documentParser;
    }

    @Override
    public List<DocumentRef> findByIds(Collection<UUID> ids) {
        return documentRepository.findAllById(ids).stream()
                .map(this::toRef)
                .toList();
    }

    @Override
    public List<String> chunkContentsForDocuments(Collection<UUID> ids) {
        return documentChunkRepository.findContentByDocumentIdIn(new HashSet<>(ids));
    }

    @Override
    public DocumentRef findForReview(UUID id) {
        return toRef(loadOrThrow(id));
    }

    @Override
    public String extractText(UUID id) {
        Document document = loadOrThrow(id);
        Path path = Paths.get(document.getFilePath());
        if (!Files.isReadable(path)) {
            log.warn("Document {} has missing file on disk: {}", id, path);
            throw new DocumentNotFoundException(id);
        }
        byte[] content = fileCryptoService.decryptFile(path);
        return documentParser.extractText(content, document.getFileType());
    }

    @Override
    public boolean knowledgeBaseMentions(String needle) {
        return documentChunkRepository.existsInKnowledgeBaseByContent("%" + escapeLike(needle) + "%");
    }

    private Document loadOrThrow(UUID id) {
        return documentRepository.findById(id)
                .orElseThrow(() -> new DocumentNotFoundException(id));
    }

    private DocumentRef toRef(Document document) {
        return new DocumentRef(document.getId(), document.getCaseId(), document.getTitle());
    }

    private String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
