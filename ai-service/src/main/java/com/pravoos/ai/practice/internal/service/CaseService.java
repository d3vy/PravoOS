package com.pravoos.ai.practice.internal.service;

import com.pravoos.ai.shared.client.UserServiceClient;
import com.pravoos.ai.shared.exception.CaseNotFoundException;
import com.pravoos.ai.shared.exception.CaseTransferNotAllowedException;
import com.pravoos.ai.shared.exception.ClientNotFoundException;
import com.pravoos.ai.shared.exception.OrganizationAccessException;
import com.pravoos.ai.practice.internal.dto.*;
import com.pravoos.ai.core.api.*;
import com.pravoos.ai.practice.internal.model.entity.Case;
import com.pravoos.ai.practice.internal.model.entity.Client;
import com.pravoos.ai.shared.model.enums.CaseStatus;
import com.pravoos.ai.practice.internal.repository.jpa.CaseHearingEventRepository;
import com.pravoos.ai.practice.internal.repository.jpa.CaseRepository;
import com.pravoos.ai.practice.internal.repository.jpa.ClientRepository;
import com.pravoos.ai.shared.util.LikePattern;
import com.pravoos.ai.shared.util.PageRequests;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class CaseService {

    private static final Logger log = LoggerFactory.getLogger(CaseService.class);
    private static final UUID NIL_ORG_SENTINEL = new UUID(0L, 0L);

    private final CaseRepository caseRepository;
    private final ClientRepository clientRepository;
    private final DocumentCommand documentCommand;
    private final DocumentQuery documentQuery;
    private final CaseHearingEventRepository hearingEventRepository;
    private final ArbitrSyncService arbitrSyncService;
    private final UserServiceClient userServiceClient;

    public CaseService(CaseRepository caseRepository,
                       ClientRepository clientRepository,
                       DocumentCommand documentCommand,
                       DocumentQuery documentQuery,
                       CaseHearingEventRepository hearingEventRepository,
                       ArbitrSyncService arbitrSyncService,
                       UserServiceClient userServiceClient) {
        this.caseRepository = caseRepository;
        this.clientRepository = clientRepository;
        this.documentCommand = documentCommand;
        this.documentQuery = documentQuery;
        this.hearingEventRepository = hearingEventRepository;
        this.arbitrSyncService = arbitrSyncService;
        this.userServiceClient = userServiceClient;
    }

    @Transactional
    public CaseResponse create(CreateCaseRequest request, UUID lawyerId, List<UUID> orgIds) {
        Client client = resolveOwnedClient(request.clientId(), lawyerId);

        Case caseEntity = new Case();
        caseEntity.setLawyerId(lawyerId);
        caseEntity.setOrgId(resolveOrgId(request.orgId(), orgIds));
        caseEntity.setTitle(request.title().trim());
        caseEntity.setDescription(request.description());
        caseEntity.setClientId(client != null ? client.getId() : null);
        caseEntity.setFilingDeadline(request.filingDeadline());
        caseEntity.setNextHearingDate(request.nextHearingDate());
        caseEntity.setExpiresAt(request.expiresAt());
        caseEntity.setArbitrCaseNumber(normalizeArbitrNumber(request.arbitrCaseNumber()));

        Case saved = caseRepository.save(caseEntity);
        log.info("Case created: '{}' ({}) by lawyer {}, client {}",
                saved.getTitle(), saved.getId(), lawyerId, saved.getClientId());
        return CaseResponse.from(saved, client != null ? client.getName() : null);
    }

    @Transactional
    public CaseResponse update(UUID caseId, UpdateCaseRequest request, UUID lawyerId, List<UUID> orgIds) {
        Case caseEntity = requireVisibleCase(caseId, lawyerId, orgIds);
        Client client = resolveOwnedClient(request.clientId(), caseEntity.getLawyerId());

        caseEntity.setTitle(request.title().trim());
        caseEntity.setDescription(request.description());
        caseEntity.setClientId(client != null ? client.getId() : null);
        caseEntity.setFilingDeadline(request.filingDeadline());
        caseEntity.setNextHearingDate(request.nextHearingDate());
        caseEntity.setExpiresAt(request.expiresAt());
        applyArbitrNumber(caseEntity, request.arbitrCaseNumber());

        log.info("Case updated: {} by lawyer {}, client {}", caseId, lawyerId, caseEntity.getClientId());
        return CaseResponse.from(caseEntity, client != null ? client.getName() : null);
    }

    @Transactional(readOnly = true)
    public List<CaseHearingEventResponse> findHearingEvents(UUID caseId, UUID lawyerId, List<UUID> orgIds) {
        requireVisibleCase(caseId, lawyerId, orgIds);
        return fetchHearingEvents(caseId);
    }

    public List<CaseHearingEventResponse> syncArbitr(UUID caseId, UUID lawyerId, List<UUID> orgIds) {
        requireVisibleCase(caseId, lawyerId, orgIds);
        arbitrSyncService.syncCase(caseId);
        return fetchHearingEvents(caseId);
    }

    private List<CaseHearingEventResponse> fetchHearingEvents(UUID caseId) {
        return hearingEventRepository.findByCaseIdOrderByEventDateDescCreatedAtDesc(caseId)
                .stream()
                .map(CaseHearingEventResponse::from)
                .toList();
    }

    private void applyArbitrNumber(Case caseEntity, String requestedNumber) {
        String normalized = normalizeArbitrNumber(requestedNumber);
        if (!Objects.equals(normalized, caseEntity.getArbitrCaseNumber())) {
            caseEntity.setArbitrCaseGuid(null);
        }
        caseEntity.setArbitrCaseNumber(normalized);
    }

    private String normalizeArbitrNumber(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    @Transactional(readOnly = true)
    public Page<CaseResponse> findByLawyer(UUID lawyerId, List<UUID> orgIds, CaseStatus status,
                                           UUID orgFilter, String query, int page, int size) {
        UUID effectiveOrgFilter = resolveOrgFilter(orgFilter, orgIds);
        String trimmedQuery = query == null ? null : query.trim();
        String pattern = (trimmedQuery == null || trimmedQuery.isEmpty()) ? null : likePattern(trimmedQuery);
        Page<Case> cases = caseRepository.findVisible(
                lawyerId, orgIdsOrSentinel(orgIds), status, effectiveOrgFilter, pattern, PageRequests.of(page, size));
        Map<UUID, String> clientNames = clientNamesForCases(cases.getContent());
        return cases.map(caseEntity ->
                CaseResponse.from(caseEntity, clientName(clientNames, caseEntity.getClientId())));
    }

    private String likePattern(String query) {
        return LikePattern.contains(query);
    }

    @Transactional
    public CaseResponse updateStatus(UUID caseId, CaseStatus status, UUID lawyerId, List<UUID> orgIds) {
        Case caseEntity = requireVisibleCase(caseId, lawyerId, orgIds);
        CaseStatus previous = caseEntity.getStatus();
        caseEntity.setStatus(status);
        log.info("Case {} status changed {} -> {} by lawyer {}", caseId, previous, status, lawyerId);
        return CaseResponse.from(caseEntity, resolveClientName(caseEntity.getClientId()));
    }

    @Transactional
    public CaseResponse changeOrg(UUID caseId, UUID targetOrgId, UUID lawyerId, List<UUID> orgIds) {
        Case caseEntity = requireOwnedCase(caseId, lawyerId);
        UUID resolvedOrgId = resolveOrgId(targetOrgId, orgIds);
        UUID previous = caseEntity.getOrgId();
        caseEntity.setOrgId(resolvedOrgId);
        log.info("Case {} org changed {} -> {} by lawyer {}", caseId, previous, resolvedOrgId, lawyerId);
        return CaseResponse.from(caseEntity, resolveClientName(caseEntity.getClientId()));
    }

    @Transactional
    public CaseResponse transferOwner(UUID caseId, UUID newOwnerId, UUID lawyerId) {
        Case caseEntity = requireOwnedCase(caseId, lawyerId);
        if (caseEntity.getOrgId() == null) {
            throw new CaseTransferNotAllowedException(
                    "Передать владельца можно только у дела, привязанного к организации");
        }
        if (newOwnerId.equals(caseEntity.getLawyerId())) {
            throw new CaseTransferNotAllowedException("Дело уже принадлежит указанному участнику");
        }
        if (!userServiceClient.isOrgMember(caseEntity.getOrgId(), newOwnerId)) {
            throw new CaseTransferNotAllowedException("Новый владелец не состоит в организации дела");
        }
        caseEntity.setLawyerId(newOwnerId);
        log.info("Case {} ownership transferred from {} to {}", caseId, lawyerId, newOwnerId);
        return CaseResponse.from(caseEntity, resolveClientName(caseEntity.getClientId()));
    }

    private String resolveClientName(UUID clientId) {
        return clientId == null
                ? null
                : clientRepository.findById(clientId).map(Client::getName).orElse(null);
    }

    @Transactional(readOnly = true)
    public CaseResponse get(UUID caseId, UUID lawyerId, List<UUID> orgIds) {
        Case caseEntity = requireVisibleCase(caseId, lawyerId, orgIds);
        return CaseResponse.from(caseEntity, resolveClientName(caseEntity.getClientId()));
    }

    @Transactional
    public void delete(UUID caseId, UUID lawyerId) {
        Case caseEntity = requireOwnedCase(caseId, lawyerId);
        documentCommand.deleteByCase(caseId);
        caseRepository.delete(caseEntity);
        log.info("Case deleted: {} by lawyer {}", caseId, lawyerId);
    }

    @Transactional
    public DocumentUploadResponse uploadDocument(UUID caseId, MultipartFile file, String title,
                                                 UUID lawyerId, List<UUID> orgIds) {
        requireVisibleCase(caseId, lawyerId, orgIds);
        return documentCommand.upload(file, title, lawyerId, caseId);
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> findDocuments(UUID caseId, UUID lawyerId, List<UUID> orgIds) {
        requireVisibleCase(caseId, lawyerId, orgIds);
        return documentQuery.findByCase(caseId);
    }

    @Transactional
    public DocumentResponse setDocumentVisibility(UUID caseId, UUID documentId, boolean visibleToClient,
                                                  UUID lawyerId, List<UUID> orgIds) {
        requireVisibleCase(caseId, lawyerId, orgIds);
        return documentCommand.setClientVisibility(documentId, caseId, visibleToClient);
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

    public Case requireVisibleCase(UUID caseId, UUID lawyerId, List<UUID> orgIds) {
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new CaseNotFoundException(caseId));
        boolean owned = caseEntity.getLawyerId().equals(lawyerId);
        boolean sharedWithOrg = caseEntity.getOrgId() != null
                && orgIds != null && orgIds.contains(caseEntity.getOrgId());
        if (!owned && !sharedWithOrg) {
            log.warn("Lawyer {} attempted to access case {} not visible to them", lawyerId, caseId);
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

    private Map<UUID, String> clientNamesForCases(List<Case> cases) {
        List<UUID> clientIds = cases.stream()
                .map(Case::getClientId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (clientIds.isEmpty()) {
            return Map.of();
        }
        return clientRepository.findAllById(clientIds).stream()
                .collect(Collectors.toMap(Client::getId, Client::getName));
    }

    private String clientName(Map<UUID, String> clientNames, UUID clientId) {
        return clientId == null ? null : clientNames.get(clientId);
    }

    private UUID resolveOrgId(UUID requestedOrgId, List<UUID> callerOrgIds) {
        if (requestedOrgId == null) {
            return null;
        }
        if (callerOrgIds == null || !callerOrgIds.contains(requestedOrgId)) {
            throw new OrganizationAccessException(requestedOrgId);
        }
        return requestedOrgId;
    }

    private UUID resolveOrgFilter(UUID orgFilter, List<UUID> callerOrgIds) {
        if (orgFilter == null) {
            return null;
        }
        if (callerOrgIds == null || !callerOrgIds.contains(orgFilter)) {
            throw new OrganizationAccessException(orgFilter);
        }
        return orgFilter;
    }

    private Collection<UUID> orgIdsOrSentinel(List<UUID> orgIds) {
        return (orgIds == null || orgIds.isEmpty()) ? List.of(NIL_ORG_SENTINEL) : orgIds;
    }
}
