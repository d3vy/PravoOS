package com.pravoos.ai.practice.internal.service;

import com.pravoos.ai.document.api.DocumentCommand;
import com.pravoos.ai.practice.internal.dto.CreateSignatureRequestDto;
import com.pravoos.ai.practice.internal.dto.SignatureRequestResponse;
import com.pravoos.ai.practice.internal.dto.SignDocumentRequest;
import com.pravoos.ai.practice.internal.dto.SignerContext;
import com.pravoos.ai.practice.internal.model.entity.Case;
import com.pravoos.ai.practice.internal.model.entity.SignatureRequest;
import com.pravoos.ai.practice.internal.repository.jpa.SignatureRequestRepository;
import com.pravoos.ai.shared.config.SignatureProperties;
import com.pravoos.ai.shared.exception.PravoosException;
import com.pravoos.ai.shared.model.enums.SignatureProviderType;
import com.pravoos.ai.shared.model.enums.SignatureStatus;
import com.pravoos.ai.shared.signature.ExternalSignatureProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
public class SignatureService {

    private static final Logger log = LoggerFactory.getLogger(SignatureService.class);

    private final SignatureRequestRepository signatureRequestRepository;
    private final CaseService caseService;
    private final DocumentCommand documentCommand;
    private final List<ExternalSignatureProvider> externalProviders;
    private final SignatureProperties signatureProperties;

    public SignatureService(SignatureRequestRepository signatureRequestRepository,
                            CaseService caseService,
                            DocumentCommand documentCommand,
                            List<ExternalSignatureProvider> externalProviders,
                            SignatureProperties signatureProperties) {
        this.signatureRequestRepository = signatureRequestRepository;
        this.caseService = caseService;
        this.documentCommand = documentCommand;
        this.externalProviders = externalProviders;
        this.signatureProperties = signatureProperties;
    }

    @Transactional
    public SignatureRequestResponse create(UUID caseId, CreateSignatureRequestDto request, UUID lawyerId) {
        Case caseEntity = caseService.requireOwnedCase(caseId, lawyerId);
        UUID signerClientId = caseEntity.getClientId();
        if (signerClientId == null) {
            throw new PravoosException("У дела не указан клиент — некому подписывать документ",
                    HttpStatus.UNPROCESSABLE_ENTITY, "CASE_HAS_NO_CLIENT");
        }

        SignatureProviderType provider = request.providerOrDefault();
        requireProviderEnabled(provider);

        String documentHash = documentCommand.contentSha256(request.documentId(), caseId);

        signatureRequestRepository
                .findByDocumentIdAndSignerClientIdAndStatus(request.documentId(), signerClientId, SignatureStatus.PENDING)
                .ifPresent(existing -> {
                    throw new PravoosException("По этому документу уже есть ожидающий запрос на подпись",
                            HttpStatus.CONFLICT, "SIGNATURE_ALREADY_PENDING");
                });

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        SignatureRequest signatureRequest = new SignatureRequest();
        signatureRequest.setDocumentId(request.documentId());
        signatureRequest.setCaseId(caseId);
        signatureRequest.setSignerClientId(signerClientId);
        signatureRequest.setRequestedBy(lawyerId);
        signatureRequest.setProvider(provider);
        signatureRequest.setStatus(SignatureStatus.PENDING);
        signatureRequest.setDocumentHash(documentHash);
        signatureRequest.setMessage(request.message());
        signatureRequest.setExpiresAt(now.plusDays(resolveExpiryDays(request.expiresInDays())));

        SignatureRequest saved = signatureRequestRepository.save(signatureRequest);
        log.info("Signature request {} created for document {} of case {} by lawyer {}",
                saved.getId(), request.documentId(), caseId, lawyerId);
        return SignatureRequestResponse.from(saved, now);
    }

    @Transactional(readOnly = true)
    public List<SignatureRequestResponse> findByCase(UUID caseId, UUID lawyerId, List<UUID> orgIds) {
        caseService.requireVisibleCase(caseId, lawyerId, orgIds);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        return signatureRequestRepository.findByCaseIdOrderByCreatedAtDesc(caseId).stream()
                .map(request -> SignatureRequestResponse.from(request, now))
                .toList();
    }

    @Transactional
    public SignatureRequestResponse cancel(UUID caseId, UUID signatureId, UUID lawyerId) {
        caseService.requireOwnedCase(caseId, lawyerId);
        SignatureRequest signatureRequest = requirePendingRequest(caseId, signatureId);
        signatureRequest.setStatus(SignatureStatus.CANCELED);
        SignatureRequest saved = signatureRequestRepository.save(signatureRequest);
        log.info("Signature request {} canceled by lawyer {}", signatureId, lawyerId);
        return SignatureRequestResponse.from(saved, LocalDateTime.now(ZoneOffset.UTC));
    }

    @Transactional
    public SignatureRequestResponse sign(UUID caseId, UUID signatureId, SignDocumentRequest request,
                                         SignerContext signer) {
        SignatureRequest signatureRequest = requirePendingRequest(caseId, signatureId);

        String currentHash = documentCommand.contentSha256(signatureRequest.getDocumentId(), caseId);
        if (!currentHash.equals(signatureRequest.getDocumentHash())) {
            log.warn("Signature {} rejected: document {} was modified after request", signatureId,
                    signatureRequest.getDocumentId());
            throw new PravoosException("Документ был изменён после создания запроса на подпись",
                    HttpStatus.CONFLICT, "DOCUMENT_MODIFIED");
        }

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        signatureRequest.setStatus(SignatureStatus.SIGNED);
        signatureRequest.setSignerName(request.signerName().trim());
        signatureRequest.setSignerUserId(signer.userId());
        signatureRequest.setSignerIp(signer.ip());
        signatureRequest.setSignerUserAgent(truncate(signer.userAgent(), 500));
        signatureRequest.setConsentText(buildConsentText(request.signerName().trim()));
        signatureRequest.setSignedAt(now);

        SignatureRequest saved = signatureRequestRepository.save(signatureRequest);
        log.info("Signature request {} signed by user {} (client {})", signatureId, signer.userId(),
                signatureRequest.getSignerClientId());
        return SignatureRequestResponse.from(saved, now);
    }

    @Transactional
    public SignatureRequestResponse decline(UUID caseId, UUID signatureId, String reason, SignerContext signer) {
        SignatureRequest signatureRequest = requirePendingRequest(caseId, signatureId);
        signatureRequest.setStatus(SignatureStatus.DECLINED);
        signatureRequest.setDeclineReason(truncate(reason, 1000));
        signatureRequest.setSignerUserId(signer.userId());
        SignatureRequest saved = signatureRequestRepository.save(signatureRequest);
        log.info("Signature request {} declined by user {}", signatureId, signer.userId());
        return SignatureRequestResponse.from(saved, LocalDateTime.now(ZoneOffset.UTC));
    }

    private SignatureRequest requirePendingRequest(UUID caseId, UUID signatureId) {
        SignatureRequest signatureRequest = signatureRequestRepository.findById(signatureId)
                .orElseThrow(() -> new PravoosException("Запрос на подпись не найден",
                        HttpStatus.NOT_FOUND, "SIGNATURE_NOT_FOUND"));
        if (!signatureRequest.getCaseId().equals(caseId)) {
            throw new PravoosException("Запрос на подпись не найден", HttpStatus.NOT_FOUND, "SIGNATURE_NOT_FOUND");
        }
        if (signatureRequest.getStatus() != SignatureStatus.PENDING) {
            throw new PravoosException("Запрос на подпись уже обработан",
                    HttpStatus.CONFLICT, "SIGNATURE_NOT_PENDING");
        }
        if (signatureRequest.isExpired(LocalDateTime.now(ZoneOffset.UTC))) {
            throw new PravoosException("Срок действия запроса на подпись истёк",
                    HttpStatus.GONE, "SIGNATURE_EXPIRED");
        }
        return signatureRequest;
    }

    private void requireProviderEnabled(SignatureProviderType provider) {
        if (provider == SignatureProviderType.SIMPLE) {
            return;
        }
        boolean enabled = externalProviders.stream()
                .anyMatch(candidate -> candidate.type() == provider && candidate.isEnabled());
        if (!enabled) {
            throw new PravoosException("Провайдер квалифицированной подписи не подключён",
                    HttpStatus.SERVICE_UNAVAILABLE, "SIGNATURE_PROVIDER_UNAVAILABLE");
        }
    }

    private int resolveExpiryDays(Integer requested) {
        if (requested != null && requested > 0) {
            return requested;
        }
        return signatureProperties.defaultExpiryDays();
    }

    private String buildConsentText(String signerName) {
        return "Я, " + signerName + ", подтверждаю согласие подписать документ простой электронной подписью "
                + "в соответствии со ст. 5 Федерального закона от 06.04.2011 № 63-ФЗ «Об электронной подписи».";
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
