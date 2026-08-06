package com.pravoos.ai.practice.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.pravoos.ai.document.api.DocumentCommand;
import com.pravoos.ai.document.api.DocumentContent;
import com.pravoos.ai.document.api.DocumentRef;
import com.pravoos.ai.practice.internal.dto.CreateSignatureRequestDto;
import com.pravoos.ai.practice.internal.dto.SignDocumentRequest;
import com.pravoos.ai.practice.internal.dto.SignatureRequestResponse;
import com.pravoos.ai.practice.internal.dto.SignerContext;
import com.pravoos.ai.practice.internal.model.entity.Case;
import com.pravoos.ai.practice.internal.model.entity.SignatureRequest;
import com.pravoos.ai.practice.internal.repository.jpa.SignatureRequestRepository;
import com.pravoos.ai.shared.config.SignatureProperties;
import com.pravoos.ai.shared.exception.PravoosException;
import com.pravoos.ai.shared.model.enums.MessageAuthorRole;
import com.pravoos.ai.shared.model.enums.SignatureProviderType;
import com.pravoos.ai.shared.model.enums.SignatureSignerRole;
import com.pravoos.ai.shared.model.enums.SignatureStatus;
import com.pravoos.ai.shared.signature.CertificateChainValidator;
import com.pravoos.ai.shared.signature.CmsTestSignatures;
import com.pravoos.ai.shared.signature.DetachedCmsVerifier;
import com.pravoos.ai.shared.signature.NoopDiadocSignatureProvider;
import com.pravoos.ai.shared.signature.TrustedCaStore;
import com.pravoos.ai.shared.util.Sha256;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;

@ExtendWith(MockitoExtension.class)
class SignatureServiceTest {

  @Mock private SignatureRequestRepository signatureRequestRepository;
  @Mock private CaseService caseService;
  @Mock private DocumentCommand documentCommand;
  @Mock private CaseMessageService caseMessageService;

  private SignatureService service;

  private final UUID caseId = UUID.randomUUID();
  private final UUID documentId = UUID.randomUUID();
  private final UUID clientId = UUID.randomUUID();
  private final UUID lawyerId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    SignatureProperties properties =
        new SignatureProperties(30, new SignatureProperties.Diadoc(null, null), null);
    service =
        new SignatureService(
            signatureRequestRepository,
            caseService,
            documentCommand,
            new DetachedCmsVerifier(new CertificateChainValidator(new TrustedCaStore(properties))),
            caseMessageService,
            new SignatureProtocolPdfWriter(),
            List.of(new NoopDiadocSignatureProvider()),
            properties);
  }

  private Case caseWithClient(UUID client) {
    Case caseEntity = new Case();
    setCaseId(caseEntity, caseId);
    caseEntity.setTitle("Дело о взыскании");
    caseEntity.setLawyerId(lawyerId);
    caseEntity.setClientId(client);
    return caseEntity;
  }

  private CreateSignatureRequestDto simpleRequest() {
    return new CreateSignatureRequestDto(
        documentId, SignatureProviderType.SIMPLE, null, null, "Подпишите", null);
  }

  private CreateSignatureRequestDto lawyerRequest(UUID signerLawyerId) {
    return new CreateSignatureRequestDto(
        documentId,
        SignatureProviderType.SIMPLE,
        SignatureSignerRole.LAWYER,
        signerLawyerId,
        "Подпишите со стороны исполнителя",
        null);
  }

  @Test
  void create_persistsPendingRequestWithDocumentHash() {
    when(caseService.requireOwnedCase(caseId, lawyerId)).thenReturn(caseWithClient(clientId));
    when(documentCommand.contentSha256(documentId, caseId)).thenReturn("hash-v1");
    when(documentCommand.clientVisibleRef(documentId, caseId)).thenReturn(documentRef());
    when(signatureRequestRepository.findByDocumentIdAndSignerClientIdAndStatus(
            documentId, clientId, SignatureStatus.PENDING))
        .thenReturn(Optional.empty());
    when(signatureRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    SignatureRequestResponse response = service.create(caseId, simpleRequest(), lawyerId);

    assertThat(response.status()).isEqualTo(SignatureStatus.PENDING);
    assertThat(response.documentHash()).isEqualTo("hash-v1");
    assertThat(response.expiresAt()).isNotNull();
  }

  @Test
  void create_rejectsCaseWithoutClient() {
    when(caseService.requireOwnedCase(caseId, lawyerId)).thenReturn(caseWithClient(null));

    assertThatThrownBy(() -> service.create(caseId, simpleRequest(), lawyerId))
        .isInstanceOf(PravoosException.class)
        .hasFieldOrPropertyWithValue("code", "CASE_HAS_NO_CLIENT");
    verify(signatureRequestRepository, never()).save(any());
  }

  @Test
  void create_rejectsDisabledExternalProvider() {
    when(caseService.requireOwnedCase(caseId, lawyerId)).thenReturn(caseWithClient(clientId));
    CreateSignatureRequestDto request =
        new CreateSignatureRequestDto(
            documentId, SignatureProviderType.DIADOC, null, null, null, null);

    assertThatThrownBy(() -> service.create(caseId, request, lawyerId))
        .isInstanceOf(PravoosException.class)
        .hasFieldOrPropertyWithValue("code", "SIGNATURE_PROVIDER_UNAVAILABLE");
    verify(documentCommand, never()).contentSha256(any(), any());
  }

  @Test
  void create_rejectsDuplicatePendingRequest() {
    when(caseService.requireOwnedCase(caseId, lawyerId)).thenReturn(caseWithClient(clientId));
    when(documentCommand.contentSha256(documentId, caseId)).thenReturn("hash-v1");
    when(documentCommand.clientVisibleRef(documentId, caseId)).thenReturn(documentRef());
    when(signatureRequestRepository.findByDocumentIdAndSignerClientIdAndStatus(
            documentId, clientId, SignatureStatus.PENDING))
        .thenReturn(Optional.of(new SignatureRequest()));

    assertThatThrownBy(() -> service.create(caseId, simpleRequest(), lawyerId))
        .isInstanceOf(PravoosException.class)
        .hasFieldOrPropertyWithValue("code", "SIGNATURE_ALREADY_PENDING");
  }

  @Test
  void sign_recordsEvidence_whenHashMatches() {
    SignatureRequest pending =
        pendingRequest("hash-v1", LocalDateTime.now(ZoneOffset.UTC).plusDays(5));
    when(signatureRequestRepository.findById(pending.getId())).thenReturn(Optional.of(pending));
    when(documentCommand.contentSha256(documentId, caseId)).thenReturn("hash-v1");
    when(signatureRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    SignerContext signer = new SignerContext(UUID.randomUUID(), "203.0.113.9", "Mozilla");
    SignatureRequestResponse response =
        service.sign(
            caseWithClient(clientId),
            pending.getId(),
            new SignDocumentRequest("Иванов Иван", true),
            signer);

    assertThat(response.status()).isEqualTo(SignatureStatus.SIGNED);
    assertThat(response.signerName()).isEqualTo("Иванов Иван");
    assertThat(response.signerIp()).isEqualTo("203.0.113.9");
    assertThat(response.signedAt()).isNotNull();
  }

  @Test
  void sign_rejectsModifiedDocument() {
    SignatureRequest pending =
        pendingRequest("hash-v1", LocalDateTime.now(ZoneOffset.UTC).plusDays(5));
    when(signatureRequestRepository.findById(pending.getId())).thenReturn(Optional.of(pending));
    when(documentCommand.contentSha256(documentId, caseId)).thenReturn("hash-v2");

    assertThatThrownBy(
            () ->
                service.sign(
                    caseWithClient(clientId),
                    pending.getId(),
                    new SignDocumentRequest("Иванов Иван", true),
                    new SignerContext(UUID.randomUUID(), "ip", "ua")))
        .isInstanceOf(PravoosException.class)
        .hasFieldOrPropertyWithValue("code", "DOCUMENT_MODIFIED");
    verify(signatureRequestRepository, never()).save(any());
  }

  @Test
  void sign_rejectsAlreadyProcessedRequest() {
    SignatureRequest signed =
        pendingRequest("hash-v1", LocalDateTime.now(ZoneOffset.UTC).plusDays(5));
    signed.setStatus(SignatureStatus.SIGNED);
    when(signatureRequestRepository.findById(signed.getId())).thenReturn(Optional.of(signed));

    assertThatThrownBy(
            () ->
                service.sign(
                    caseWithClient(clientId),
                    signed.getId(),
                    new SignDocumentRequest("Иванов Иван", true),
                    new SignerContext(UUID.randomUUID(), "ip", "ua")))
        .isInstanceOf(PravoosException.class)
        .hasFieldOrPropertyWithValue("code", "SIGNATURE_NOT_PENDING");
  }

  @Test
  void sign_rejectsExpiredRequest() {
    SignatureRequest expired =
        pendingRequest("hash-v1", LocalDateTime.now(ZoneOffset.UTC).minusDays(1));
    when(signatureRequestRepository.findById(expired.getId())).thenReturn(Optional.of(expired));

    assertThatThrownBy(
            () ->
                service.sign(
                    caseWithClient(clientId),
                    expired.getId(),
                    new SignDocumentRequest("Иванов Иван", true),
                    new SignerContext(UUID.randomUUID(), "ip", "ua")))
        .isInstanceOf(PravoosException.class)
        .hasFieldOrPropertyWithValue("code", "SIGNATURE_EXPIRED");
  }

  @Test
  void decline_marksRequestDeclined() {
    SignatureRequest pending =
        pendingRequest("hash-v1", LocalDateTime.now(ZoneOffset.UTC).plusDays(5));
    when(signatureRequestRepository.findById(pending.getId())).thenReturn(Optional.of(pending));
    when(signatureRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    SignatureRequestResponse response =
        service.decline(
            caseWithClient(clientId),
            pending.getId(),
            "Не согласен",
            new SignerContext(UUID.randomUUID(), "ip", "ua"));

    assertThat(response.status()).isEqualTo(SignatureStatus.DECLINED);
    assertThat(response.declineReason()).isEqualTo("Не согласен");
  }

  @Test
  void cancel_marksRequestCanceled() {
    SignatureRequest pending =
        pendingRequest("hash-v1", LocalDateTime.now(ZoneOffset.UTC).plusDays(5));
    when(caseService.requireOwnedCase(caseId, lawyerId)).thenReturn(caseWithClient(clientId));
    when(signatureRequestRepository.findById(pending.getId())).thenReturn(Optional.of(pending));
    when(signatureRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    SignatureRequestResponse response = service.cancel(caseId, pending.getId(), lawyerId);

    assertThat(response.status()).isEqualTo(SignatureStatus.CANCELED);
  }

  @Test
  void requestForForeignCase_isNotFound() {
    SignatureRequest pending =
        pendingRequest("hash-v1", LocalDateTime.now(ZoneOffset.UTC).plusDays(5));
    pending.setCaseId(UUID.randomUUID());
    when(signatureRequestRepository.findById(pending.getId())).thenReturn(Optional.of(pending));

    assertThatThrownBy(
            () ->
                service.decline(
                    caseWithClient(clientId),
                    pending.getId(),
                    null,
                    new SignerContext(UUID.randomUUID(), "ip", "ua")))
        .isInstanceOf(PravoosException.class)
        .hasFieldOrPropertyWithValue("code", "SIGNATURE_NOT_FOUND");
  }

  @Test
  void signWithCms_storesCertificateEvidence() {
    byte[] content = "Договор".getBytes(StandardCharsets.UTF_8);
    SignatureRequest pending = cmsPendingRequest(Sha256.hex(content));
    when(signatureRequestRepository.findById(pending.getId())).thenReturn(Optional.of(pending));
    when(documentCommand.loadClientContent(documentId, caseId))
        .thenReturn(documentContent(content));
    when(signatureRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    byte[] signatureFile = CmsTestSignatures.detachedSignature(content, "Иванов Иван Иванович");
    SignatureRequestResponse response =
        service.signWithCms(
            caseWithClient(clientId),
            pending.getId(),
            signatureFile,
            "contract.sig",
            new SignerContext(UUID.randomUUID(), "ip", "ua"));

    assertThat(response.status()).isEqualTo(SignatureStatus.SIGNED);
    assertThat(response.signerName()).isEqualTo("Иванов Иван Иванович");
    assertThat(response.certificateSerial()).isNotBlank();
    assertThat(response.hasSignatureFile()).isTrue();
    assertThat(pending.getConsentText()).contains("ст. 6");
  }

  @Test
  void signWithCms_rejectsSignatureOfAnotherDocument() {
    byte[] content = "Договор".getBytes(StandardCharsets.UTF_8);
    SignatureRequest pending = cmsPendingRequest(Sha256.hex(content));
    when(signatureRequestRepository.findById(pending.getId())).thenReturn(Optional.of(pending));
    when(documentCommand.loadClientContent(documentId, caseId))
        .thenReturn(documentContent(content));

    byte[] foreignSignature =
        CmsTestSignatures.detachedSignature(
            "Другой документ".getBytes(StandardCharsets.UTF_8), "Иванов Иван");

    assertThatThrownBy(
            () ->
                service.signWithCms(
                    caseWithClient(clientId),
                    pending.getId(),
                    foreignSignature,
                    "contract.sig",
                    new SignerContext(UUID.randomUUID(), "ip", "ua")))
        .isInstanceOf(PravoosException.class)
        .hasFieldOrPropertyWithValue("code", "INVALID_SIGNATURE_FILE");
    verify(signatureRequestRepository, never()).save(any());
  }

  @Test
  void signWithCms_rejectsRequestCreatedForSimpleSignature() {
    SignatureRequest pending =
        pendingRequest("hash-v1", LocalDateTime.now(ZoneOffset.UTC).plusDays(5));
    when(signatureRequestRepository.findById(pending.getId())).thenReturn(Optional.of(pending));

    assertThatThrownBy(
            () ->
                service.signWithCms(
                    caseWithClient(clientId),
                    pending.getId(),
                    new byte[] {1, 2, 3},
                    "contract.sig",
                    new SignerContext(UUID.randomUUID(), "ip", "ua")))
        .isInstanceOf(PravoosException.class)
        .hasFieldOrPropertyWithValue("code", "SIGNATURE_PROVIDER_MISMATCH");
  }

  @Test
  void sign_rejectsSimpleSignatureOnQualifiedRequest() {
    SignatureRequest pending = cmsPendingRequest("hash-v1");
    when(signatureRequestRepository.findById(pending.getId())).thenReturn(Optional.of(pending));

    assertThatThrownBy(
            () ->
                service.sign(
                    caseWithClient(clientId),
                    pending.getId(),
                    new SignDocumentRequest("Иванов Иван", true),
                    new SignerContext(UUID.randomUUID(), "ip", "ua")))
        .isInstanceOf(PravoosException.class)
        .hasFieldOrPropertyWithValue("code", "SIGNATURE_PROVIDER_MISMATCH");
  }

  @Test
  void exportProtocol_producesPdfForSignedRequest() {
    SignatureRequest signed =
        pendingRequest("hash-v1", LocalDateTime.now(ZoneOffset.UTC).plusDays(5));
    signed.setStatus(SignatureStatus.SIGNED);
    signed.setSignerName("Иванов Иван");
    signed.setConsentText("Согласие");
    signed.setSignedAt(LocalDateTime.now(ZoneOffset.UTC));
    when(signatureRequestRepository.findById(signed.getId())).thenReturn(Optional.of(signed));
    when(documentCommand.clientVisibleRef(documentId, caseId)).thenReturn(documentRef());

    byte[] protocol = service.exportProtocol(caseWithClient(clientId), signed.getId());

    assertThat(protocol).isNotEmpty();
    assertThat(new String(protocol, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
  }

  @Test
  void exportProtocol_rejectsPendingRequest() {
    SignatureRequest pending =
        pendingRequest("hash-v1", LocalDateTime.now(ZoneOffset.UTC).plusDays(5));
    when(signatureRequestRepository.findById(pending.getId())).thenReturn(Optional.of(pending));

    assertThatThrownBy(() -> service.exportProtocol(caseWithClient(clientId), pending.getId()))
        .isInstanceOf(PravoosException.class)
        .hasFieldOrPropertyWithValue("code", "SIGNATURE_NOT_SIGNED");
  }

  @Test
  void create_forLawyerSigner_usesCaseScopedDocumentAndDefaultsToCaseOwner() {
    when(caseService.requireOwnedCase(caseId, lawyerId)).thenReturn(caseWithClient(clientId));
    when(documentCommand.caseContentSha256(documentId, caseId)).thenReturn("hash-v1");
    when(documentCommand.caseRef(documentId, caseId)).thenReturn(documentRef());
    when(signatureRequestRepository.findByDocumentIdAndSignerLawyerIdAndStatus(
            documentId, lawyerId, SignatureStatus.PENDING))
        .thenReturn(Optional.empty());
    when(signatureRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    SignatureRequestResponse response = service.create(caseId, lawyerRequest(null), lawyerId);

    assertThat(response.signerRole()).isEqualTo(SignatureSignerRole.LAWYER);
    assertThat(response.signerLawyerId()).isEqualTo(lawyerId);
    verify(documentCommand, never()).clientVisibleRef(any(), any());
    verifyNoInteractions(caseMessageService);
  }

  @Test
  void create_forLawyerSigner_rejectsSignerOutsideCase() {
    Case caseEntity = caseWithClient(clientId);
    UUID outsider = UUID.randomUUID();
    when(caseService.requireOwnedCase(caseId, lawyerId)).thenReturn(caseEntity);
    when(caseService.isCaseParticipant(caseEntity, outsider)).thenReturn(false);

    assertThatThrownBy(() -> service.create(caseId, lawyerRequest(outsider), lawyerId))
        .isInstanceOf(PravoosException.class)
        .hasFieldOrPropertyWithValue("code", "SIGNER_NOT_CASE_MEMBER");
    verify(signatureRequestRepository, never()).save(any());
  }

  @Test
  void create_forLawyerSigner_allowsCaseWithoutClient() {
    when(caseService.requireOwnedCase(caseId, lawyerId)).thenReturn(caseWithClient(null));
    when(documentCommand.caseContentSha256(documentId, caseId)).thenReturn("hash-v1");
    when(documentCommand.caseRef(documentId, caseId)).thenReturn(documentRef());
    when(signatureRequestRepository.findByDocumentIdAndSignerLawyerIdAndStatus(
            documentId, lawyerId, SignatureStatus.PENDING))
        .thenReturn(Optional.empty());
    when(signatureRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    assertThat(service.create(caseId, lawyerRequest(null), lawyerId).status())
        .isEqualTo(SignatureStatus.PENDING);
  }

  @Test
  void signAsLawyer_recordsEvidenceForDesignatedSigner() {
    SignatureRequest pending = lawyerPendingRequest(lawyerId);
    when(signatureRequestRepository.findById(pending.getId())).thenReturn(Optional.of(pending));
    when(documentCommand.caseContentSha256(documentId, caseId)).thenReturn("hash-v1");
    when(signatureRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    SignatureRequestResponse response =
        service.signAsLawyer(
            caseWithClient(clientId),
            pending.getId(),
            new SignDocumentRequest("Смирнова Анна", true),
            new SignerContext(lawyerId, "198.51.100.4", "Mozilla"));

    assertThat(response.status()).isEqualTo(SignatureStatus.SIGNED);
    assertThat(response.signerName()).isEqualTo("Смирнова Анна");
    verify(caseMessageService)
        .postSystemMessage(any(), eq(MessageAuthorRole.LAWYER), eq(lawyerId), contains("юристом"));
  }

  @Test
  void signAsLawyer_rejectsAnotherLawyer() {
    SignatureRequest pending = lawyerPendingRequest(UUID.randomUUID());
    when(signatureRequestRepository.findById(pending.getId())).thenReturn(Optional.of(pending));

    assertThatThrownBy(
            () ->
                service.signAsLawyer(
                    caseWithClient(clientId),
                    pending.getId(),
                    new SignDocumentRequest("Смирнова Анна", true),
                    new SignerContext(lawyerId, "ip", "ua")))
        .isInstanceOf(PravoosException.class)
        .hasFieldOrPropertyWithValue("code", "SIGNATURE_SIGNER_MISMATCH");
    verify(signatureRequestRepository, never()).save(any());
  }

  @Test
  void signAsLawyer_rejectsClientSignerRequest() {
    SignatureRequest pending =
        pendingRequest("hash-v1", LocalDateTime.now(ZoneOffset.UTC).plusDays(5));
    when(signatureRequestRepository.findById(pending.getId())).thenReturn(Optional.of(pending));

    assertThatThrownBy(
            () ->
                service.signAsLawyer(
                    caseWithClient(clientId),
                    pending.getId(),
                    new SignDocumentRequest("Смирнова Анна", true),
                    new SignerContext(lawyerId, "ip", "ua")))
        .isInstanceOf(PravoosException.class)
        .hasFieldOrPropertyWithValue("code", "SIGNATURE_SIGNER_ROLE_MISMATCH");
  }

  @Test
  void sign_rejectsLawyerSignerRequestFromClientPath() {
    SignatureRequest pending = lawyerPendingRequest(lawyerId);
    when(signatureRequestRepository.findById(pending.getId())).thenReturn(Optional.of(pending));

    assertThatThrownBy(
            () ->
                service.sign(
                    caseWithClient(clientId),
                    pending.getId(),
                    new SignDocumentRequest("Иванов Иван", true),
                    new SignerContext(UUID.randomUUID(), "ip", "ua")))
        .isInstanceOf(PravoosException.class)
        .hasFieldOrPropertyWithValue("code", "SIGNATURE_SIGNER_ROLE_MISMATCH");
  }

  @Test
  void signWithCmsAsLawyer_verifiesContainerAgainstCaseDocument() {
    byte[] content = "Договор".getBytes(StandardCharsets.UTF_8);
    SignatureRequest pending = lawyerPendingRequest(lawyerId);
    pending.setProvider(SignatureProviderType.DETACHED_CMS);
    pending.setDocumentHash(Sha256.hex(content));
    when(signatureRequestRepository.findById(pending.getId())).thenReturn(Optional.of(pending));
    when(documentCommand.loadCaseContent(documentId, caseId)).thenReturn(documentContent(content));
    when(signatureRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    SignatureRequestResponse response =
        service.signWithCmsAsLawyer(
            caseWithClient(clientId),
            pending.getId(),
            CmsTestSignatures.detachedSignature(content, "Смирнова Анна"),
            "contract.sig",
            new SignerContext(lawyerId, "ip", "ua"));

    assertThat(response.status()).isEqualTo(SignatureStatus.SIGNED);
    assertThat(response.hasSignatureFile()).isTrue();
    verify(documentCommand, never()).loadClientContent(any(), any());
  }

  @Test
  void declineAsLawyer_marksRequestDeclined() {
    SignatureRequest pending = lawyerPendingRequest(lawyerId);
    when(signatureRequestRepository.findById(pending.getId())).thenReturn(Optional.of(pending));
    when(signatureRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    SignatureRequestResponse response =
        service.declineAsLawyer(
            caseWithClient(clientId),
            pending.getId(),
            "Нужна правка формулировки",
            new SignerContext(lawyerId, "ip", "ua"));

    assertThat(response.status()).isEqualTo(SignatureStatus.DECLINED);
    verify(caseMessageService)
        .postSystemMessage(any(), eq(MessageAuthorRole.LAWYER), eq(lawyerId), contains("Юрист"));
  }

  private SignatureRequest lawyerPendingRequest(UUID signerLawyerId) {
    SignatureRequest request =
        pendingRequest("hash-v1", LocalDateTime.now(ZoneOffset.UTC).plusDays(5));
    request.setSignerRole(SignatureSignerRole.LAWYER);
    request.setSignerClientId(null);
    request.setSignerLawyerId(signerLawyerId);
    return request;
  }

  private DocumentContent documentContent(byte[] content) {
    return new DocumentContent(
        new ByteArrayResource(content), "contract.pdf", "pdf", content.length);
  }

  private DocumentRef documentRef() {
    return new DocumentRef(documentId, caseId, lawyerId, "Договор оказания услуг");
  }

  private SignatureRequest cmsPendingRequest(String hash) {
    SignatureRequest request = pendingRequest(hash, LocalDateTime.now(ZoneOffset.UTC).plusDays(5));
    request.setProvider(SignatureProviderType.DETACHED_CMS);
    return request;
  }

  private SignatureRequest pendingRequest(String hash, LocalDateTime expiresAt) {
    SignatureRequest request = new SignatureRequest();
    setId(request, UUID.randomUUID());
    request.setCaseId(caseId);
    request.setDocumentId(documentId);
    request.setSignerRole(SignatureSignerRole.CLIENT);
    request.setSignerClientId(clientId);
    request.setProvider(SignatureProviderType.SIMPLE);
    request.setStatus(SignatureStatus.PENDING);
    request.setDocumentHash(hash);
    request.setExpiresAt(expiresAt);
    return request;
  }

  private void setCaseId(Case caseEntity, UUID id) {
    try {
      var field = Case.class.getDeclaredField("id");
      field.setAccessible(true);
      field.set(caseEntity, id);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }

  private void setId(SignatureRequest request, UUID id) {
    try {
      var field = SignatureRequest.class.getDeclaredField("id");
      field.setAccessible(true);
      field.set(request, id);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }
}
