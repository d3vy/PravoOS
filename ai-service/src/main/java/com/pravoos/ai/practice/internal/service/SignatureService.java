package com.pravoos.ai.practice.internal.service;

import com.pravoos.ai.document.api.DocumentCommand;
import com.pravoos.ai.document.api.DocumentContent;
import com.pravoos.ai.document.api.DocumentRef;
import com.pravoos.ai.practice.internal.dto.CreateSignatureRequestDto;
import com.pravoos.ai.practice.internal.dto.SignDocumentRequest;
import com.pravoos.ai.practice.internal.dto.SignatureProtocolModel;
import com.pravoos.ai.practice.internal.dto.SignatureRequestResponse;
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
import com.pravoos.ai.shared.model.enums.SignatureSignerRole;
import com.pravoos.ai.shared.model.enums.SignatureStatus;
import com.pravoos.ai.shared.signature.CmsSignatureDetails;
import com.pravoos.ai.shared.signature.DetachedCmsVerifier;
import com.pravoos.ai.shared.signature.ExternalSignatureProvider;
import com.pravoos.ai.shared.util.Sha256;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

  public SignatureService(
      SignatureRequestRepository signatureRequestRepository,
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
  public SignatureRequestResponse create(
      UUID caseId, CreateSignatureRequestDto request, UUID lawyerId) {
    Case caseEntity = caseService.requireOwnedCase(caseId, lawyerId);
    SignatureSignerRole signerRole = request.signerRoleOrDefault();
    SignatureProviderType provider = request.providerOrDefault();
    requireProviderEnabled(provider);

    UUID signerClientId =
        signerRole == SignatureSignerRole.CLIENT ? requireCaseClient(caseEntity) : null;
    UUID signerLawyerId =
        signerRole == SignatureSignerRole.LAWYER
            ? resolveSignerLawyer(caseEntity, request.signerLawyerId())
            : null;

    DocumentRef document = documentRef(signerRole, request.documentId(), caseId);
    String documentHash = documentHash(signerRole, request.documentId(), caseId);
    requireNoPendingRequest(request.documentId(), signerRole, signerClientId, signerLawyerId);

    LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
    SignatureRequest signatureRequest = new SignatureRequest();
    signatureRequest.setDocumentId(request.documentId());
    signatureRequest.setCaseId(caseId);
    signatureRequest.setSignerRole(signerRole);
    signatureRequest.setSignerClientId(signerClientId);
    signatureRequest.setSignerLawyerId(signerLawyerId);
    signatureRequest.setRequestedBy(lawyerId);
    signatureRequest.setProvider(provider);
    signatureRequest.setStatus(SignatureStatus.PENDING);
    signatureRequest.setDocumentHash(documentHash);
    signatureRequest.setMessage(request.message());
    signatureRequest.setExpiresAt(now.plusDays(resolveExpiryDays(request.expiresInDays())));

    SignatureRequest saved = signatureRequestRepository.save(signatureRequest);
    if (signerRole == SignatureSignerRole.CLIENT) {
      notifyClientRequested(caseEntity, saved, document.title(), lawyerId);
    }
    log.info(
        "Signature request {} created for document {} of case {} by lawyer {} (signer role {})",
        saved.getId(),
        request.documentId(),
        caseId,
        lawyerId,
        signerRole);
    return SignatureRequestResponse.from(saved, now);
  }

  @Transactional(readOnly = true)
  public List<SignatureRequestResponse> findPendingForLawyer(UUID lawyerId) {
    LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
    return signatureRequestRepository
        .findBySignerLawyerIdAndStatusOrderByCreatedAtDesc(lawyerId, SignatureStatus.PENDING)
        .stream()
        .filter(request -> !request.isExpired(now))
        .map(request -> SignatureRequestResponse.from(request, now))
        .toList();
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
  public SignatureRequestResponse sign(
      Case caseEntity, UUID signatureId, SignDocumentRequest request, SignerContext signer) {
    SignatureRequest signatureRequest = requirePendingRequest(caseEntity.getId(), signatureId);
    requireSignerRole(signatureRequest, SignatureSignerRole.CLIENT);
    return applySimpleSignature(caseEntity, signatureRequest, request, signer);
  }

  @Transactional
  public SignatureRequestResponse signAsLawyer(
      Case caseEntity, UUID signatureId, SignDocumentRequest request, SignerContext signer) {
    SignatureRequest signatureRequest = requirePendingRequest(caseEntity.getId(), signatureId);
    requireDesignatedLawyer(signatureRequest, signer.userId());
    return applySimpleSignature(caseEntity, signatureRequest, request, signer);
  }

  @Transactional
  public SignatureRequestResponse signWithCmsAsLawyer(
      Case caseEntity,
      UUID signatureId,
      byte[] signatureFile,
      String signatureFileName,
      SignerContext signer) {
    SignatureRequest signatureRequest = requirePendingRequest(caseEntity.getId(), signatureId);
    requireDesignatedLawyer(signatureRequest, signer.userId());
    return applyCmsSignature(
        caseEntity, signatureRequest, signatureFile, signatureFileName, signer);
  }

  @Transactional
  public SignatureRequestResponse declineAsLawyer(
      Case caseEntity, UUID signatureId, String reason, SignerContext signer) {
    SignatureRequest signatureRequest = requirePendingRequest(caseEntity.getId(), signatureId);
    requireDesignatedLawyer(signatureRequest, signer.userId());
    return applyDecline(caseEntity, signatureRequest, reason, signer);
  }

  private SignatureRequestResponse applySimpleSignature(
      Case caseEntity,
      SignatureRequest signatureRequest,
      SignDocumentRequest request,
      SignerContext signer) {
    requireProvider(signatureRequest, SignatureProviderType.SIMPLE);
    requireUnmodifiedDocument(
        signatureRequest,
        documentHash(
            signatureRequest.getSignerRole(),
            signatureRequest.getDocumentId(),
            caseEntity.getId()));

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
    notifySigned(caseEntity, saved, signer.userId());
    log.info(
        "Signature request {} signed by user {} as {}",
        saved.getId(),
        signer.userId(),
        saved.getSignerRole());
    return SignatureRequestResponse.from(saved, now);
  }

  @Transactional
  public SignatureRequestResponse signWithCms(
      Case caseEntity,
      UUID signatureId,
      byte[] signatureFile,
      String signatureFileName,
      SignerContext signer) {
    SignatureRequest signatureRequest = requirePendingRequest(caseEntity.getId(), signatureId);
    requireSignerRole(signatureRequest, SignatureSignerRole.CLIENT);
    return applyCmsSignature(
        caseEntity, signatureRequest, signatureFile, signatureFileName, signer);
  }

  private SignatureRequestResponse applyCmsSignature(
      Case caseEntity,
      SignatureRequest signatureRequest,
      byte[] signatureFile,
      String signatureFileName,
      SignerContext signer) {
    requireProvider(signatureRequest, SignatureProviderType.DETACHED_CMS);
    assertSignatureFileSize(signatureFile);

    byte[] content =
        readDocumentContent(
            signatureRequest.getSignerRole(), signatureRequest.getDocumentId(), caseEntity.getId());
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
    signatureRequest.setChainVerified(details.chainVerified() ? Boolean.TRUE : null);
    signatureRequest.setSignedAt(now);

    SignatureRequest saved = signatureRequestRepository.save(signatureRequest);
    notifySigned(caseEntity, saved, signer.userId());
    log.info(
        "Signature request {} signed with detached CMS by user {} as {} (cert {})",
        saved.getId(),
        signer.userId(),
        saved.getSignerRole(),
        details.certificateSerial());
    return SignatureRequestResponse.from(saved, now);
  }

  @Transactional
  public SignatureRequestResponse decline(
      Case caseEntity, UUID signatureId, String reason, SignerContext signer) {
    SignatureRequest signatureRequest = requirePendingRequest(caseEntity.getId(), signatureId);
    requireSignerRole(signatureRequest, SignatureSignerRole.CLIENT);
    return applyDecline(caseEntity, signatureRequest, reason, signer);
  }

  private SignatureRequestResponse applyDecline(
      Case caseEntity, SignatureRequest signatureRequest, String reason, SignerContext signer) {
    signatureRequest.setStatus(SignatureStatus.DECLINED);
    signatureRequest.setDeclineReason(truncate(reason, 1000));
    signatureRequest.setSignerUserId(signer.userId());
    SignatureRequest saved = signatureRequestRepository.save(signatureRequest);
    notifyDeclined(caseEntity, saved, signer.userId());
    log.info(
        "Signature request {} declined by user {} as {}",
        saved.getId(),
        signer.userId(),
        saved.getSignerRole());
    return SignatureRequestResponse.from(saved, LocalDateTime.now(ZoneOffset.UTC));
  }

  @Transactional(readOnly = true)
  public byte[] exportProtocol(Case caseEntity, UUID signatureId) {
    SignatureRequest signatureRequest = requireSignedRequest(caseEntity.getId(), signatureId);
    DocumentRef document =
        documentRef(
            signatureRequest.getSignerRole(), signatureRequest.getDocumentId(), caseEntity.getId());
    return signatureProtocolPdfWriter.write(
        toProtocolModel(caseEntity, signatureRequest, document.title()));
  }

  @Transactional(readOnly = true)
  public SignatureFileDownload downloadSignatureFile(Case caseEntity, UUID signatureId) {
    SignatureRequest signatureRequest = requireSignedRequest(caseEntity.getId(), signatureId);
    if (signatureRequest.getSignatureData() == null) {
      throw new PravoosException(
          "У этой подписи нет файла открепленной подписи",
          HttpStatus.NOT_FOUND,
          "SIGNATURE_FILE_NOT_FOUND");
    }
    String fileName =
        signatureRequest.getSignatureFileName() != null
            ? signatureRequest.getSignatureFileName()
            : "signature-" + signatureId + ".sig";
    return new SignatureFileDownload(
        Base64.getDecoder().decode(signatureRequest.getSignatureData()), fileName);
  }

  public record SignatureFileDownload(byte[] content, String fileName) {}

  private SignatureProtocolModel toProtocolModel(
      Case caseEntity, SignatureRequest request, String documentTitle) {
    return new SignatureProtocolModel(
        request.getId(),
        caseEntity.getTitle(),
        documentTitle,
        request.getDocumentHash(),
        request.getProvider(),
        request.getSignerRole(),
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
        request.getSignatureAlgorithm(),
        Boolean.TRUE.equals(request.getChainVerified()));
  }

  private UUID requireCaseClient(Case caseEntity) {
    UUID clientId = caseEntity.getClientId();
    if (clientId == null) {
      throw new PravoosException(
          "У дела не указан клиент — некому подписывать документ",
          HttpStatus.UNPROCESSABLE_ENTITY,
          "CASE_HAS_NO_CLIENT");
    }
    return clientId;
  }

  private UUID resolveSignerLawyer(Case caseEntity, UUID requestedSignerLawyerId) {
    if (requestedSignerLawyerId == null) {
      return caseEntity.getLawyerId();
    }
    if (!caseService.isCaseParticipant(caseEntity, requestedSignerLawyerId)) {
      throw new PravoosException(
          "Подписант не имеет доступа к делу",
          HttpStatus.UNPROCESSABLE_ENTITY,
          "SIGNER_NOT_CASE_MEMBER");
    }
    return requestedSignerLawyerId;
  }

  private void requireNoPendingRequest(
      UUID documentId, SignatureSignerRole signerRole, UUID signerClientId, UUID signerLawyerId) {
    boolean pendingExists =
        signerRole == SignatureSignerRole.CLIENT
            ? signatureRequestRepository
                .findByDocumentIdAndSignerClientIdAndStatus(
                    documentId, signerClientId, SignatureStatus.PENDING)
                .isPresent()
            : signatureRequestRepository
                .findByDocumentIdAndSignerLawyerIdAndStatus(
                    documentId, signerLawyerId, SignatureStatus.PENDING)
                .isPresent();
    if (pendingExists) {
      throw new PravoosException(
          "По этому документу уже есть ожидающий запрос на подпись",
          HttpStatus.CONFLICT,
          "SIGNATURE_ALREADY_PENDING");
    }
  }

  private DocumentRef documentRef(SignatureSignerRole signerRole, UUID documentId, UUID caseId) {
    return signerRole == SignatureSignerRole.CLIENT
        ? documentCommand.clientVisibleRef(documentId, caseId)
        : documentCommand.caseRef(documentId, caseId);
  }

  private String documentHash(SignatureSignerRole signerRole, UUID documentId, UUID caseId) {
    return signerRole == SignatureSignerRole.CLIENT
        ? documentCommand.contentSha256(documentId, caseId)
        : documentCommand.caseContentSha256(documentId, caseId);
  }

  private byte[] readDocumentContent(SignatureSignerRole signerRole, UUID documentId, UUID caseId) {
    DocumentContent content =
        signerRole == SignatureSignerRole.CLIENT
            ? documentCommand.loadClientContent(documentId, caseId)
            : documentCommand.loadCaseContent(documentId, caseId);
    try {
      return content.resource().getContentAsByteArray();
    } catch (IOException ex) {
      throw new CaseExportException(
          "Не удалось прочитать содержимое документа для проверки подписи", ex);
    }
  }

  private void assertSignatureFileSize(byte[] signatureFile) {
    if (signatureFile == null || signatureFile.length == 0) {
      throw new InvalidSignatureFileException("файл пуст");
    }
    if (signatureFile.length > MAX_SIGNATURE_FILE_BYTES) {
      throw new InvalidSignatureFileException(
          "размер превышает "
              + MAX_SIGNATURE_FILE_BYTES / 1024
              + " КБ — это не контейнер открепленной подписи");
    }
  }

  private void requireUnmodifiedDocument(SignatureRequest signatureRequest, String currentHash) {
    if (!currentHash.equals(signatureRequest.getDocumentHash())) {
      log.warn(
          "Signature {} rejected: document {} was modified after request",
          signatureRequest.getId(),
          signatureRequest.getDocumentId());
      throw new PravoosException(
          "Документ был изменён после создания запроса на подпись",
          HttpStatus.CONFLICT,
          "DOCUMENT_MODIFIED");
    }
  }

  private void requireSignerRole(SignatureRequest signatureRequest, SignatureSignerRole expected) {
    if (signatureRequest.getSignerRole() != expected) {
      throw new PravoosException(
          "Этот запрос предназначен другой стороне подписания",
          HttpStatus.CONFLICT,
          "SIGNATURE_SIGNER_ROLE_MISMATCH");
    }
  }

  private void requireDesignatedLawyer(SignatureRequest signatureRequest, UUID lawyerId) {
    requireSignerRole(signatureRequest, SignatureSignerRole.LAWYER);
    if (!lawyerId.equals(signatureRequest.getSignerLawyerId())) {
      log.warn(
          "Lawyer {} attempted to act on signature {} designated to {}",
          lawyerId,
          signatureRequest.getId(),
          signatureRequest.getSignerLawyerId());
      throw new PravoosException(
          "Подписать документ может только назначенный подписант",
          HttpStatus.FORBIDDEN,
          "SIGNATURE_SIGNER_MISMATCH");
    }
  }

  private void requireProvider(SignatureRequest signatureRequest, SignatureProviderType expected) {
    if (signatureRequest.getProvider() != expected) {
      throw new PravoosException(
          "Этот запрос требует другого способа подписи",
          HttpStatus.CONFLICT,
          "SIGNATURE_PROVIDER_MISMATCH");
    }
  }

  private SignatureRequest requirePendingRequest(UUID caseId, UUID signatureId) {
    SignatureRequest signatureRequest = requireRequest(caseId, signatureId);
    if (signatureRequest.getStatus() != SignatureStatus.PENDING) {
      throw new PravoosException(
          "Запрос на подпись уже обработан", HttpStatus.CONFLICT, "SIGNATURE_NOT_PENDING");
    }
    if (signatureRequest.isExpired(LocalDateTime.now(ZoneOffset.UTC))) {
      throw new PravoosException(
          "Срок действия запроса на подпись истёк", HttpStatus.GONE, "SIGNATURE_EXPIRED");
    }
    return signatureRequest;
  }

  private SignatureRequest requireSignedRequest(UUID caseId, UUID signatureId) {
    SignatureRequest signatureRequest = requireRequest(caseId, signatureId);
    if (signatureRequest.getStatus() != SignatureStatus.SIGNED) {
      throw new PravoosException(
          "Протокол доступен только для подписанного документа",
          HttpStatus.CONFLICT,
          "SIGNATURE_NOT_SIGNED");
    }
    return signatureRequest;
  }

  private SignatureRequest requireRequest(UUID caseId, UUID signatureId) {
    SignatureRequest signatureRequest =
        signatureRequestRepository
            .findById(signatureId)
            .orElseThrow(
                () ->
                    new PravoosException(
                        "Запрос на подпись не найден",
                        HttpStatus.NOT_FOUND,
                        "SIGNATURE_NOT_FOUND"));
    if (!signatureRequest.getCaseId().equals(caseId)) {
      throw new PravoosException(
          "Запрос на подпись не найден", HttpStatus.NOT_FOUND, "SIGNATURE_NOT_FOUND");
    }
    return signatureRequest;
  }

  private void requireProviderEnabled(SignatureProviderType provider) {
    if (provider == SignatureProviderType.SIMPLE
        || provider == SignatureProviderType.DETACHED_CMS) {
      return;
    }
    boolean enabled =
        externalProviders.stream()
            .anyMatch(candidate -> candidate.type() == provider && candidate.isEnabled());
    if (!enabled) {
      throw new PravoosException(
          "Провайдер квалифицированной подписи не подключён",
          HttpStatus.SERVICE_UNAVAILABLE,
          "SIGNATURE_PROVIDER_UNAVAILABLE");
    }
  }

  private void notifyClientRequested(
      Case caseEntity, SignatureRequest request, String documentTitle, UUID lawyerId) {
    StringBuilder body =
        new StringBuilder("Запрос на подпись документа «").append(documentTitle).append("»");
    if (request.getProvider() == SignatureProviderType.DETACHED_CMS) {
      body.append(
          ". Требуется квалифицированная подпись — приложите файл открепленной подписи (.sig)");
    }
    if (request.getMessage() != null && !request.getMessage().isBlank()) {
      body.append(". ").append(request.getMessage());
    }
    caseMessageService.postSystemMessage(
        caseEntity, MessageAuthorRole.LAWYER, lawyerId, body.toString());
  }

  private void notifySigned(Case caseEntity, SignatureRequest request, UUID signerUserId) {
    String kind =
        request.getProvider() == SignatureProviderType.DETACHED_CMS
            ? "квалифицированной электронной подписью"
            : "простой электронной подписью";
    caseMessageService.postSystemMessage(
        caseEntity,
        authorRoleOf(request),
        signerUserId,
        "Документ подписан "
            + signerSideLabel(request)
            + " "
            + kind
            + ": "
            + request.getSignerName());
  }

  private void notifyDeclined(Case caseEntity, SignatureRequest request, UUID signerUserId) {
    String reason =
        request.getDeclineReason() == null || request.getDeclineReason().isBlank()
            ? "без указания причины"
            : request.getDeclineReason();
    caseMessageService.postSystemMessage(
        caseEntity,
        authorRoleOf(request),
        signerUserId,
        signerSideNominative(request) + " отклонил подписание документа: " + reason);
  }

  private MessageAuthorRole authorRoleOf(SignatureRequest request) {
    return request.getSignerRole() == SignatureSignerRole.LAWYER
        ? MessageAuthorRole.LAWYER
        : MessageAuthorRole.CLIENT;
  }

  private String signerSideLabel(SignatureRequest request) {
    return request.getSignerRole() == SignatureSignerRole.LAWYER ? "юристом" : "клиентом";
  }

  private String signerSideNominative(SignatureRequest request) {
    return request.getSignerRole() == SignatureSignerRole.LAWYER ? "Юрист" : "Клиент";
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
    return "Я, "
        + signerName
        + ", подтверждаю согласие подписать документ простой электронной подписью "
        + "в соответствии со ст. 5 Федерального закона от 06.04.2011 № 63-ФЗ «Об электронной подписи».";
  }

  private String qualifiedConsentText(CmsSignatureDetails details) {
    return "Документ подписан усиленной электронной подписью (открепленный контейнер CMS/PKCS#7) "
        + "владельцем сертификата "
        + resolveCertificateSignerName(details)
        + " в соответствии со ст. 6 Федерального закона от 06.04.2011 № 63-ФЗ «Об электронной подписи». "
        + "Подпись математически проверена против содержимого документа."
        + (details.chainVerified()
            ? " Цепочка сертификата проверена до доверенного аккредитованного удостоверяющего центра."
            : "");
  }

  private String truncate(String value, int maxLength) {
    if (value == null) {
      return null;
    }
    return value.length() <= maxLength ? value : value.substring(0, maxLength);
  }
}
