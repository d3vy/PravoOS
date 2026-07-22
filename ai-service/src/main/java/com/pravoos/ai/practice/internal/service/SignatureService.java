package com.pravoos.ai.practice.internal.service;

import com.pravoos.ai.document.api.DocumentCommand;
import com.pravoos.ai.document.api.DocumentContent;
import com.pravoos.ai.document.api.DocumentRef;
import com.pravoos.ai.practice.internal.dto.CreateSignatureRequestDto;
import com.pravoos.ai.practice.internal.dto.SignatureProtocolModel;
import com.pravoos.ai.practice.internal.dto.SignatureRequestResponse;
import com.pravoos.ai.practice.internal.dto.SignDocumentRequest;
import com.pravoos.ai.practice.internal.dto.SignerContext;
import com.pravoos.ai.practice.internal.model.entity.Case;
import com.pravoos.ai.practice.internal.model.entity.SignatureRequest;
import com.pravoos.ai.practice.internal.repository.jpa.SignatureRequestRepository;
import com.pravoos.ai.shared.config.SignatureProperties;
import com.pravoos.ai.shared.exception.CaseExportException;
import com.pravoos.ai.shared.exception.InvalidSignatureFileException;
import com.pravoos.ai.shared.exception.PravoosException;
import com.pravoos.ai.shared.model.enums.MessageAuthorRole;
import com.pravoos.ai.shared.model.enums.SignatureProviderType;
import com.pravoos.ai.shared.model.enums.SignatureStatus;
import com.pravoos.ai.shared.signature.CmsSignatureDetails;
import com.pravoos.ai.shared.signature.DetachedCmsVerifier;
import com.pravoos.ai.shared.signature.ExternalSignatureProvider;
import com.pravoos.ai.shared.util.Sha256;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Service
public class SignatureService {

    private static final Logger log = LoggerFactory.getLogger(SignatureService.class);
    private static final int MAX_SIGNATURE_FILE_BYTES = 512 * 1024;

    private final SignatureRequestRepository signatureRequestRepository;
    private final CaseService caseService;
    private final DocumentCommand documentCommand;
    private final DetachedCmsVerifier detachedCmsVerifier;
    private final CaseMessageService caseMessageService;
    private final SignatureProtocolPdfWriter signatureProtocolPdfWriter;
    private final List<ExternalSignatureProvider> externalProviders;
    private final SignatureProperties signatureProperties;

    public SignatureService(SignatureRequestRepository signatureRequestRepository,
                            CaseService caseService,
                            DocumentCommand documentCommand,
                            DetachedCmsVerifier detachedCmsVerifier,
                            CaseMessageService caseMessageService,
                            SignatureProtocolPdfWriter signatureProtocolPdfWriter,
                            List<ExternalSignatureProvider> externalProviders,
                            SignatureProperties signatureProperties) {
        this.signatureRequestRepository = signatureRequestRepository;
        this.caseService = caseService;
        this.documentCommand = documentCommand;
        this.detachedCmsVerifier = detachedCmsVerifier;
        this.caseMessageService = caseMessageService;
        this.signatureProtocolPdfWriter = signatureProtocolPdfWriter;
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

        DocumentRef document = documentCommand.clientVisibleRef(request.documentId(), caseId);
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
        notifyClientRequested(caseEntity, saved, document.title(), lawyerId);
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
    public SignatureRequestResponse sign(Case caseEntity, UUID signatureId, SignDocumentRequest request,
                                         SignerContext signer) {
        SignatureRequest signatureRequest = requirePendingRequest(caseEntity.getId(), signatureId);
        requireProvider(signatureRequest, SignatureProviderType.SIMPLE);
        requireUnmodifiedDocument(signatureRequest,
                documentCommand.contentSha256(signatureRequest.getDocumentId(), caseEntity.getId()));

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        String signerName = request.signerName().trim();
        signatureRequest.setStatus(SignatureStatus.SIGNED);
        signatureRequest.setSignerName(signerName);
        signatureRequest.setSignerUserId(signer.userId());
        signatureRequest.setSignerIp(signer.ip());
        signatureRequest.setSignerUserAgent(truncate(signer.userAgent(), 500));
        signatureRequest.setConsentText(simpleConsentText(signerName));
        signatureRequest.setSignedAt(now);

        SignatureRequest saved = signatureRequestRepository.save(signatureRequest);
        notifyLawyerSigned(caseEntity, saved, signer.userId());
        log.info("Signature request {} signed by user {} (client {})", signatureId, signer.userId(),
                signatureRequest.getSignerClientId());
        return SignatureRequestResponse.from(saved, now);
    }

    @Transactional
    public SignatureRequestResponse signWithCms(Case caseEntity, UUID signatureId, byte[] signatureFile,
                                                String signatureFileName, SignerContext signer) {
        SignatureRequest signatureRequest = requirePendingRequest(caseEntity.getId(), signatureId);
        requireProvider(signatureRequest, SignatureProviderType.DETACHED_CMS);
        assertSignatureFileSize(signatureFile);

        byte[] content = readDocumentContent(signatureRequest.getDocumentId(), caseEntity.getId());
        requireUnmodifiedDocument(signatureRequest, Sha256.hex(content));

        CmsSignatureDetails details = detachedCmsVerifier.verify(signatureFile, content);

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        signatureRequest.setStatus(SignatureStatus.SIGNED);
        signatureRequest.setSignerName(resolveCertificateSignerName(details));
        signatureRequest.setSignerUserId(signer.userId());
        signatureRequest.setSignerIp(signer.ip());
        signatureRequest.setSignerUserAgent(truncate(signer.userAgent(), 500));
        signatureRequest.setConsentText(qualifiedConsentText(details));
        signatureRequest.setSignatureData(Base64.getEncoder().encodeToString(signatureFile));
        signatureRequest.setSignatureFileName(truncate(signatureFileName, 300));
        signatureRequest.setSignatureAlgorithm(truncate(details.signatureAlgorithm(), 120));
        signatureRequest.setCertificateSubject(truncate(details.certificateSubject(), 1000));
        signatureRequest.setCertificateIssuer(truncate(details.certificateIssuer(), 1000));
        signatureRequest.setCertificateSerial(truncate(details.certificateSerial(), 100));
        signatureRequest.setCertificateValidFrom(details.certificateValidFrom());
        signatureRequest.setCertificateValidTo(details.certificateValidTo());
        signatureRequest.setDeclaredSigningTime(details.signingTime());
        signatureRequest.setSignedAt(now);

        SignatureRequest saved = signatureRequestRepository.save(signatureRequest);
        notifyLawyerSigned(caseEntity, saved, signer.userId());
        log.info("Signature request {} signed with detached CMS by user {} (cert {})", signatureId,
                signer.userId(), details.certificateSerial());
        return SignatureRequestResponse.from(saved, now);
    }

    @Transactional
    public SignatureRequestResponse decline(Case caseEntity, UUID signatureId, String reason, SignerContext signer) {
        SignatureRequest signatureRequest = requirePendingRequest(caseEntity.getId(), signatureId);
        signatureRequest.setStatus(SignatureStatus.DECLINED);
        signatureRequest.setDeclineReason(truncate(reason, 1000));
        signatureRequest.setSignerUserId(signer.userId());
        SignatureRequest saved = signatureRequestRepository.save(signatureRequest);
        notifyLawyerDeclined(caseEntity, saved, signer.userId());
        log.info("Signature request {} declined by user {}", signatureId, signer.userId());
        return SignatureRequestResponse.from(saved, LocalDateTime.now(ZoneOffset.UTC));
    }

    @Transactional(readOnly = true)
    public byte[] exportProtocol(Case caseEntity, UUID signatureId) {
        SignatureRequest signatureRequest = requireSignedRequest(caseEntity.getId(), signatureId);
        DocumentRef document = documentCommand.clientVisibleRef(signatureRequest.getDocumentId(), caseEntity.getId());
        return signatureProtocolPdfWriter.write(toProtocolModel(caseEntity, signatureRequest, document.title()));
    }

    @Transactional(readOnly = true)
    public SignatureFileDownload downloadSignatureFile(Case caseEntity, UUID signatureId) {
        SignatureRequest signatureRequest = requireSignedRequest(caseEntity.getId(), signatureId);
        if (signatureRequest.getSignatureData() == null) {
            throw new PravoosException("У этой подписи нет файла открепленной подписи",
                    HttpStatus.NOT_FOUND, "SIGNATURE_FILE_NOT_FOUND");
        }
        String fileName = signatureRequest.getSignatureFileName() != null
                ? signatureRequest.getSignatureFileName()
                : "signature-" + signatureId + ".sig";
        return new SignatureFileDownload(Base64.getDecoder().decode(signatureRequest.getSignatureData()), fileName);
    }

    public record SignatureFileDownload(byte[] content, String fileName) {
    }

    private SignatureProtocolModel toProtocolModel(Case caseEntity, SignatureRequest request, String documentTitle) {
        return new SignatureProtocolModel(
                request.getId(),
                caseEntity.getTitle(),
                documentTitle,
                request.getDocumentHash(),
                request.getProvider(),
                request.getSignerName(),
                request.getSignerIp(),
                request.getSignerUserAgent(),
                request.getConsentText(),
                request.getCreatedAt(),
                request.getSignedAt(),
                request.getDeclaredSigningTime(),
                request.getCertificateSubject(),
                request.getCertificateIssuer(),
                request.getCertificateSerial(),
                request.getCertificateValidFrom(),
                request.getCertificateValidTo(),
                request.getSignatureAlgorithm());
    }

    private byte[] readDocumentContent(UUID documentId, UUID caseId) {
        DocumentContent content = documentCommand.loadClientContent(documentId, caseId);
        try {
            return content.resource().getContentAsByteArray();
        } catch (IOException ex) {
            throw new CaseExportException("Не удалось прочитать содержимое документа для проверки подписи", ex);
        }
    }

    private void assertSignatureFileSize(byte[] signatureFile) {
        if (signatureFile == null || signatureFile.length == 0) {
            throw new InvalidSignatureFileException("файл пуст");
        }
        if (signatureFile.length > MAX_SIGNATURE_FILE_BYTES) {
            throw new InvalidSignatureFileException("размер превышает "
                    + MAX_SIGNATURE_FILE_BYTES / 1024 + " КБ — это не контейнер открепленной подписи");
        }
    }

    private void requireUnmodifiedDocument(SignatureRequest signatureRequest, String currentHash) {
        if (!currentHash.equals(signatureRequest.getDocumentHash())) {
            log.warn("Signature {} rejected: document {} was modified after request", signatureRequest.getId(),
                    signatureRequest.getDocumentId());
            throw new PravoosException("Документ был изменён после создания запроса на подпись",
                    HttpStatus.CONFLICT, "DOCUMENT_MODIFIED");
        }
    }

    private void requireProvider(SignatureRequest signatureRequest, SignatureProviderType expected) {
        if (signatureRequest.getProvider() != expected) {
            throw new PravoosException("Этот запрос требует другого способа подписи",
                    HttpStatus.CONFLICT, "SIGNATURE_PROVIDER_MISMATCH");
        }
    }

    private SignatureRequest requirePendingRequest(UUID caseId, UUID signatureId) {
        SignatureRequest signatureRequest = requireRequest(caseId, signatureId);
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

    private SignatureRequest requireSignedRequest(UUID caseId, UUID signatureId) {
        SignatureRequest signatureRequest = requireRequest(caseId, signatureId);
        if (signatureRequest.getStatus() != SignatureStatus.SIGNED) {
            throw new PravoosException("Протокол доступен только для подписанного документа",
                    HttpStatus.CONFLICT, "SIGNATURE_NOT_SIGNED");
        }
        return signatureRequest;
    }

    private SignatureRequest requireRequest(UUID caseId, UUID signatureId) {
        SignatureRequest signatureRequest = signatureRequestRepository.findById(signatureId)
                .orElseThrow(() -> new PravoosException("Запрос на подпись не найден",
                        HttpStatus.NOT_FOUND, "SIGNATURE_NOT_FOUND"));
        if (!signatureRequest.getCaseId().equals(caseId)) {
            throw new PravoosException("Запрос на подпись не найден", HttpStatus.NOT_FOUND, "SIGNATURE_NOT_FOUND");
        }
        return signatureRequest;
    }

    private void requireProviderEnabled(SignatureProviderType provider) {
        if (provider == SignatureProviderType.SIMPLE || provider == SignatureProviderType.DETACHED_CMS) {
            return;
        }
        boolean enabled = externalProviders.stream()
                .anyMatch(candidate -> candidate.type() == provider && candidate.isEnabled());
        if (!enabled) {
            throw new PravoosException("Провайдер квалифицированной подписи не подключён",
                    HttpStatus.SERVICE_UNAVAILABLE, "SIGNATURE_PROVIDER_UNAVAILABLE");
        }
    }

    private void notifyClientRequested(Case caseEntity, SignatureRequest request, String documentTitle,
                                       UUID lawyerId) {
        StringBuilder body = new StringBuilder("Запрос на подпись документа «").append(documentTitle).append("»");
        if (request.getProvider() == SignatureProviderType.DETACHED_CMS) {
            body.append(". Требуется квалифицированная подпись — приложите файл открепленной подписи (.sig)");
        }
        if (request.getMessage() != null && !request.getMessage().isBlank()) {
            body.append(". ").append(request.getMessage());
        }
        caseMessageService.postSystemMessage(caseEntity, MessageAuthorRole.LAWYER, lawyerId, body.toString());
    }

    private void notifyLawyerSigned(Case caseEntity, SignatureRequest request, UUID signerUserId) {
        String kind = request.getProvider() == SignatureProviderType.DETACHED_CMS
                ? "квалифицированной электронной подписью"
                : "простой электронной подписью";
        caseMessageService.postSystemMessage(caseEntity, MessageAuthorRole.CLIENT, signerUserId,
                "Документ подписан " + kind + ": " + request.getSignerName());
    }

    private void notifyLawyerDeclined(Case caseEntity, SignatureRequest request, UUID signerUserId) {
        String reason = request.getDeclineReason() == null || request.getDeclineReason().isBlank()
                ? "без указания причины"
                : request.getDeclineReason();
        caseMessageService.postSystemMessage(caseEntity, MessageAuthorRole.CLIENT, signerUserId,
                "Клиент отклонил подписание документа: " + reason);
    }

    private int resolveExpiryDays(Integer requested) {
        if (requested != null && requested > 0) {
            return requested;
        }
        return signatureProperties.defaultExpiryDays();
    }

    private String resolveCertificateSignerName(CmsSignatureDetails details) {
        String commonName = details.signerCommonName();
        if (commonName != null && !commonName.isBlank()) {
            return truncate(commonName, 300);
        }
        return truncate(details.certificateSubject(), 300);
    }

    private String simpleConsentText(String signerName) {
        return "Я, " + signerName + ", подтверждаю согласие подписать документ простой электронной подписью "
                + "в соответствии со ст. 5 Федерального закона от 06.04.2011 № 63-ФЗ «Об электронной подписи».";
    }

    private String qualifiedConsentText(CmsSignatureDetails details) {
        return "Документ подписан усиленной электронной подписью (открепленный контейнер CMS/PKCS#7) "
                + "владельцем сертификата " + resolveCertificateSignerName(details)
                + " в соответствии со ст. 6 Федерального закона от 06.04.2011 № 63-ФЗ «Об электронной подписи». "
                + "Подпись математически проверена против содержимого документа.";
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
