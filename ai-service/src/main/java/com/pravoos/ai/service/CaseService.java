package com.pravoos.ai.service;

import com.pravoos.ai.exception.CaseNotFoundException;
import com.pravoos.ai.exception.ClientNotFoundException;
import com.pravoos.ai.model.dto.CaseResponse;
import com.pravoos.ai.model.dto.CreateCaseRequest;
import com.pravoos.ai.model.dto.DocumentResponse;
import com.pravoos.ai.model.dto.DocumentUploadResponse;
import com.pravoos.ai.model.dto.UpdateCaseRequest;
import com.pravoos.ai.model.entity.Case;
import com.pravoos.ai.model.entity.Client;
import com.pravoos.ai.model.enums.CaseStatus;
import com.pravoos.ai.repository.jpa.CaseRepository;
import com.pravoos.ai.repository.jpa.ClientRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class CaseService {

    private static final Logger log = LoggerFactory.getLogger(CaseService.class);

    private final CaseRepository caseRepository;
    private final ClientRepository clientRepository;
    private final DocumentService documentService;

    public CaseService(CaseRepository caseRepository,
                       ClientRepository clientRepository,
                       DocumentService documentService) {
        this.caseRepository = caseRepository;
        this.clientRepository = clientRepository;
        this.documentService = documentService;
    }

    @Transactional
    public CaseResponse create(CreateCaseRequest request, UUID lawyerId) {
        Client client = resolveOwnedClient(request.clientId(), lawyerId);

        Case caseEntity = new Case();
        caseEntity.setLawyerId(lawyerId);
        caseEntity.setTitle(request.title().trim());
        caseEntity.setDescription(request.description());
        caseEntity.setClientId(client != null ? client.getId() : null);
        caseEntity.setFilingDeadline(request.filingDeadline());
        caseEntity.setNextHearingDate(request.nextHearingDate());
        caseEntity.setExpiresAt(request.expiresAt());

        Case saved = caseRepository.save(caseEntity);
        log.info("Case created: '{}' ({}) by lawyer {}, client {}",
                saved.getTitle(), saved.getId(), lawyerId, saved.getClientId());
        return CaseResponse.from(saved, client != null ? client.getName() : null);
    }

    @Transactional
    public CaseResponse update(UUID caseId, UpdateCaseRequest request, UUID lawyerId) {
        Case caseEntity = requireOwnedCase(caseId, lawyerId);
        Client client = resolveOwnedClient(request.clientId(), lawyerId);

        caseEntity.setTitle(request.title().trim());
        caseEntity.setDescription(request.description());
        caseEntity.setClientId(client != null ? client.getId() : null);
        caseEntity.setFilingDeadline(request.filingDeadline());
        caseEntity.setNextHearingDate(request.nextHearingDate());
        caseEntity.setExpiresAt(request.expiresAt());

        log.info("Case updated: {} by lawyer {}, client {}", caseId, lawyerId, caseEntity.getClientId());
        return CaseResponse.from(caseEntity, client != null ? client.getName() : null);
    }

    @Transactional(readOnly = true)
    public List<CaseResponse> findByLawyer(UUID lawyerId, CaseStatus status) {
        Map<UUID, String> clientNames = clientNamesFor(lawyerId);
        List<Case> cases = status == null
                ? caseRepository.findByLawyerIdOrderByCreatedAtDesc(lawyerId)
                : caseRepository.findByLawyerIdAndStatusOrderByCreatedAtDesc(lawyerId, status);
        return cases.stream()
                .map(caseEntity -> CaseResponse.from(caseEntity, clientName(clientNames, caseEntity.getClientId())))
                .toList();
    }

    @Transactional
    public CaseResponse updateStatus(UUID caseId, CaseStatus status, UUID lawyerId) {
        Case caseEntity = requireOwnedCase(caseId, lawyerId);
        CaseStatus previous = caseEntity.getStatus();
        caseEntity.setStatus(status);
        log.info("Case {} status changed {} -> {} by lawyer {}", caseId, previous, status, lawyerId);
        String clientName = caseEntity.getClientId() == null
                ? null
                : clientRepository.findById(caseEntity.getClientId()).map(Client::getName).orElse(null);
        return CaseResponse.from(caseEntity, clientName);
    }

    @Transactional(readOnly = true)
    public CaseResponse get(UUID caseId, UUID lawyerId) {
        Case caseEntity = requireOwnedCase(caseId, lawyerId);
        String clientName = caseEntity.getClientId() == null
                ? null
                : clientRepository.findById(caseEntity.getClientId()).map(Client::getName).orElse(null);
        return CaseResponse.from(caseEntity, clientName);
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

    private Client resolveOwnedClient(UUID clientId, UUID lawyerId) {
        if (clientId == null) {
            return null;
        }
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new ClientNotFoundException(clientId));
        if (!client.getLawyerId().equals(lawyerId)) {
            log.warn("Lawyer {} attempted to attach client {} owned by another user", lawyerId, clientId);
            throw new ClientNotFoundException(clientId);
        }
        return client;
    }

    private Map<UUID, String> clientNamesFor(UUID lawyerId) {
        return clientRepository.findByLawyerIdOrderByCreatedAtDesc(lawyerId)
                .stream()
                .collect(Collectors.toMap(Client::getId, Client::getName));
    }

    private String clientName(Map<UUID, String> clientNames, UUID clientId) {
        return clientId == null ? null : clientNames.get(clientId);
    }
}
