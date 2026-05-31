package com.pravoos.ai.service;

import com.pravoos.ai.exception.CaseNotFoundException;
import com.pravoos.ai.model.dto.CaseResponse;
import com.pravoos.ai.model.dto.CreateCaseRequest;
import com.pravoos.ai.model.dto.DocumentResponse;
import com.pravoos.ai.model.dto.DocumentUploadResponse;
import com.pravoos.ai.model.entity.Case;
import com.pravoos.ai.repository.jpa.CaseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Service
public class CaseService {

    private static final Logger log = LoggerFactory.getLogger(CaseService.class);

    private final CaseRepository caseRepository;
    private final DocumentService documentService;

    public CaseService(CaseRepository caseRepository, DocumentService documentService) {
        this.caseRepository = caseRepository;
        this.documentService = documentService;
    }

    @Transactional
    public CaseResponse create(CreateCaseRequest request, UUID lawyerId) {
        Case caseEntity = new Case();
        caseEntity.setLawyerId(lawyerId);
        caseEntity.setTitle(request.title().trim());
        caseEntity.setDescription(request.description());

        Case saved = caseRepository.save(caseEntity);
        log.info("Case created: '{}' ({}) by lawyer {}", saved.getTitle(), saved.getId(), lawyerId);
        return CaseResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<CaseResponse> findByLawyer(UUID lawyerId) {
        return caseRepository.findByLawyerIdOrderByCreatedAtDesc(lawyerId)
                .stream()
                .map(CaseResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public CaseResponse get(UUID caseId, UUID lawyerId) {
        return CaseResponse.from(requireOwnedCase(caseId, lawyerId));
    }

    @Transactional
    public void delete(UUID caseId, UUID lawyerId) {
        Case caseEntity = requireOwnedCase(caseId, lawyerId);
        documentService.deleteByCase(caseId);
        caseRepository.delete(caseEntity);
        log.info("Case deleted: {} by lawyer {}", caseId, lawyerId);
    }

    @Transactional
    public DocumentUploadResponse uploadDocument(UUID caseId, MultipartFile file, String title, UUID lawyerId) {
        requireOwnedCase(caseId, lawyerId);
        return documentService.upload(file, title, lawyerId, caseId);
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> findDocuments(UUID caseId, UUID lawyerId) {
        requireOwnedCase(caseId, lawyerId);
        return documentService.findByCase(caseId);
    }

    public Case requireOwnedCase(UUID caseId, UUID lawyerId) {
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new CaseNotFoundException(caseId));
        if (!caseEntity.getLawyerId().equals(lawyerId)) {
            log.warn("Lawyer {} attempted to access case {} owned by another user", lawyerId, caseId);
            throw new CaseNotFoundException(caseId);
        }
        return caseEntity;
    }
}
