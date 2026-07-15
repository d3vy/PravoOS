package com.pravoos.ai.practice.internal.service;

import com.pravoos.ai.practice.internal.dto.SignatureRequestResponse;
import com.pravoos.ai.practice.internal.dto.SignDocumentRequest;
import com.pravoos.ai.practice.internal.dto.SignerContext;
import com.pravoos.ai.practice.internal.model.entity.SignatureRequest;
import com.pravoos.ai.practice.internal.repository.jpa.SignatureRequestRepository;
import com.pravoos.ai.shared.exception.PravoosException;
import com.pravoos.ai.shared.model.enums.SignatureStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
public class PortalSignatureService {

    private final SignatureRequestRepository signatureRequestRepository;
    private final PortalCaseService portalCaseService;
    private final SignatureService signatureService;

    public PortalSignatureService(SignatureRequestRepository signatureRequestRepository,
                                  PortalCaseService portalCaseService,
                                  SignatureService signatureService) {
        this.signatureRequestRepository = signatureRequestRepository;
        this.portalCaseService = portalCaseService;
        this.signatureService = signatureService;
    }

    @Transactional(readOnly = true)
    public List<SignatureRequestResponse> listPending(List<UUID> clientIds) {
        if (clientIds == null || clientIds.isEmpty()) {
            return List.of();
        }
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        return signatureRequestRepository
                .findBySignerClientIdInAndStatusOrderByCreatedAtDesc(clientIds, SignatureStatus.PENDING).stream()
                .filter(request -> !request.isExpired(now))
                .map(request -> SignatureRequestResponse.from(request, now))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SignatureRequestResponse> listByCase(UUID caseId, List<UUID> clientIds) {
        portalCaseService.requireClientCase(caseId, clientIds);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        return signatureRequestRepository.findByCaseIdOrderByCreatedAtDesc(caseId).stream()
                .map(request -> SignatureRequestResponse.from(request, now))
                .toList();
    }

    @Transactional
    public SignatureRequestResponse sign(UUID signatureId, SignDocumentRequest request,
                                         List<UUID> clientIds, SignerContext signer) {
        UUID caseId = requireClientSignatureCase(signatureId, clientIds);
        return signatureService.sign(caseId, signatureId, request, signer);
    }

    @Transactional
    public SignatureRequestResponse decline(UUID signatureId, String reason,
                                            List<UUID> clientIds, SignerContext signer) {
        UUID caseId = requireClientSignatureCase(signatureId, clientIds);
        return signatureService.decline(caseId, signatureId, reason, signer);
    }

    private UUID requireClientSignatureCase(UUID signatureId, List<UUID> clientIds) {
        SignatureRequest signatureRequest = signatureRequestRepository.findById(signatureId)
                .orElseThrow(() -> new PravoosException("Запрос на подпись не найден",
                        HttpStatus.NOT_FOUND, "SIGNATURE_NOT_FOUND"));
        portalCaseService.requireClientCase(signatureRequest.getCaseId(), clientIds);
        return signatureRequest.getCaseId();
    }
}
