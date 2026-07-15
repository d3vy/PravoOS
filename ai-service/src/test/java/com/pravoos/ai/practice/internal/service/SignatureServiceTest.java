package com.pravoos.ai.practice.internal.service;

import com.pravoos.ai.document.api.DocumentCommand;
import com.pravoos.ai.practice.internal.dto.CreateSignatureRequestDto;
import com.pravoos.ai.practice.internal.dto.SignDocumentRequest;
import com.pravoos.ai.practice.internal.dto.SignatureRequestResponse;
import com.pravoos.ai.practice.internal.dto.SignerContext;
import com.pravoos.ai.practice.internal.model.entity.Case;
import com.pravoos.ai.practice.internal.model.entity.SignatureRequest;
import com.pravoos.ai.practice.internal.repository.jpa.SignatureRequestRepository;
import com.pravoos.ai.shared.config.SignatureProperties;
import com.pravoos.ai.shared.exception.PravoosException;
import com.pravoos.ai.shared.model.enums.SignatureProviderType;
import com.pravoos.ai.shared.model.enums.SignatureStatus;
import com.pravoos.ai.shared.signature.NoopDiadocSignatureProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SignatureServiceTest {

    @Mock private SignatureRequestRepository signatureRequestRepository;
    @Mock private CaseService caseService;
    @Mock private DocumentCommand documentCommand;

    private SignatureService service;

    private final UUID caseId = UUID.randomUUID();
    private final UUID documentId = UUID.randomUUID();
    private final UUID clientId = UUID.randomUUID();
    private final UUID lawyerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        SignatureProperties properties = new SignatureProperties(30,
                new SignatureProperties.Diadoc(null, null));
        service = new SignatureService(signatureRequestRepository, caseService, documentCommand,
                List.of(new NoopDiadocSignatureProvider()), properties);
    }

    private Case caseWithClient(UUID client) {
        Case caseEntity = new Case();
        caseEntity.setClientId(client);
        return caseEntity;
    }

    private CreateSignatureRequestDto simpleRequest() {
        return new CreateSignatureRequestDto(documentId, SignatureProviderType.SIMPLE, "Подпишите", null);
    }

    @Test
    void create_persistsPendingRequestWithDocumentHash() {
        when(caseService.requireOwnedCase(caseId, lawyerId)).thenReturn(caseWithClient(clientId));
        when(documentCommand.contentSha256(documentId, caseId)).thenReturn("hash-v1");
        when(signatureRequestRepository.findByDocumentIdAndSignerClientIdAndStatus(
                documentId, clientId, SignatureStatus.PENDING)).thenReturn(Optional.empty());
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
        CreateSignatureRequestDto request = new CreateSignatureRequestDto(documentId, SignatureProviderType.DIADOC, null, null);

        assertThatThrownBy(() -> service.create(caseId, request, lawyerId))
                .isInstanceOf(PravoosException.class)
                .hasFieldOrPropertyWithValue("code", "SIGNATURE_PROVIDER_UNAVAILABLE");
        verify(documentCommand, never()).contentSha256(any(), any());
    }

    @Test
    void create_rejectsDuplicatePendingRequest() {
        when(caseService.requireOwnedCase(caseId, lawyerId)).thenReturn(caseWithClient(clientId));
        when(documentCommand.contentSha256(documentId, caseId)).thenReturn("hash-v1");
        when(signatureRequestRepository.findByDocumentIdAndSignerClientIdAndStatus(
                documentId, clientId, SignatureStatus.PENDING))
                .thenReturn(Optional.of(new SignatureRequest()));

        assertThatThrownBy(() -> service.create(caseId, simpleRequest(), lawyerId))
                .isInstanceOf(PravoosException.class)
                .hasFieldOrPropertyWithValue("code", "SIGNATURE_ALREADY_PENDING");
    }

    @Test
    void sign_recordsEvidence_whenHashMatches() {
        SignatureRequest pending = pendingRequest("hash-v1", LocalDateTime.now(ZoneOffset.UTC).plusDays(5));
        when(signatureRequestRepository.findById(pending.getId())).thenReturn(Optional.of(pending));
        when(documentCommand.contentSha256(documentId, caseId)).thenReturn("hash-v1");
        when(signatureRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SignerContext signer = new SignerContext(UUID.randomUUID(), "203.0.113.9", "Mozilla");
        SignatureRequestResponse response = service.sign(caseId, pending.getId(),
                new SignDocumentRequest("Иванов Иван", true), signer);

        assertThat(response.status()).isEqualTo(SignatureStatus.SIGNED);
        assertThat(response.signerName()).isEqualTo("Иванов Иван");
        assertThat(response.signerIp()).isEqualTo("203.0.113.9");
        assertThat(response.signedAt()).isNotNull();
    }

    @Test
    void sign_rejectsModifiedDocument() {
        SignatureRequest pending = pendingRequest("hash-v1", LocalDateTime.now(ZoneOffset.UTC).plusDays(5));
        when(signatureRequestRepository.findById(pending.getId())).thenReturn(Optional.of(pending));
        when(documentCommand.contentSha256(documentId, caseId)).thenReturn("hash-v2");

        assertThatThrownBy(() -> service.sign(caseId, pending.getId(),
                new SignDocumentRequest("Иванов Иван", true), new SignerContext(UUID.randomUUID(), "ip", "ua")))
                .isInstanceOf(PravoosException.class)
                .hasFieldOrPropertyWithValue("code", "DOCUMENT_MODIFIED");
        verify(signatureRequestRepository, never()).save(any());
    }

    @Test
    void sign_rejectsAlreadyProcessedRequest() {
        SignatureRequest signed = pendingRequest("hash-v1", LocalDateTime.now(ZoneOffset.UTC).plusDays(5));
        signed.setStatus(SignatureStatus.SIGNED);
        when(signatureRequestRepository.findById(signed.getId())).thenReturn(Optional.of(signed));

        assertThatThrownBy(() -> service.sign(caseId, signed.getId(),
                new SignDocumentRequest("Иванов Иван", true), new SignerContext(UUID.randomUUID(), "ip", "ua")))
                .isInstanceOf(PravoosException.class)
                .hasFieldOrPropertyWithValue("code", "SIGNATURE_NOT_PENDING");
    }

    @Test
    void sign_rejectsExpiredRequest() {
        SignatureRequest expired = pendingRequest("hash-v1", LocalDateTime.now(ZoneOffset.UTC).minusDays(1));
        when(signatureRequestRepository.findById(expired.getId())).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.sign(caseId, expired.getId(),
                new SignDocumentRequest("Иванов Иван", true), new SignerContext(UUID.randomUUID(), "ip", "ua")))
                .isInstanceOf(PravoosException.class)
                .hasFieldOrPropertyWithValue("code", "SIGNATURE_EXPIRED");
    }

    @Test
    void decline_marksRequestDeclined() {
        SignatureRequest pending = pendingRequest("hash-v1", LocalDateTime.now(ZoneOffset.UTC).plusDays(5));
        when(signatureRequestRepository.findById(pending.getId())).thenReturn(Optional.of(pending));
        when(signatureRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SignatureRequestResponse response = service.decline(caseId, pending.getId(), "Не согласен",
                new SignerContext(UUID.randomUUID(), "ip", "ua"));

        assertThat(response.status()).isEqualTo(SignatureStatus.DECLINED);
        assertThat(response.declineReason()).isEqualTo("Не согласен");
    }

    @Test
    void cancel_marksRequestCanceled() {
        SignatureRequest pending = pendingRequest("hash-v1", LocalDateTime.now(ZoneOffset.UTC).plusDays(5));
        when(caseService.requireOwnedCase(caseId, lawyerId)).thenReturn(caseWithClient(clientId));
        when(signatureRequestRepository.findById(pending.getId())).thenReturn(Optional.of(pending));
        when(signatureRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SignatureRequestResponse response = service.cancel(caseId, pending.getId(), lawyerId);

        assertThat(response.status()).isEqualTo(SignatureStatus.CANCELED);
    }

    @Test
    void requestForForeignCase_isNotFound() {
        SignatureRequest pending = pendingRequest("hash-v1", LocalDateTime.now(ZoneOffset.UTC).plusDays(5));
        pending.setCaseId(UUID.randomUUID());
        when(signatureRequestRepository.findById(pending.getId())).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> service.decline(caseId, pending.getId(), null,
                new SignerContext(UUID.randomUUID(), "ip", "ua")))
                .isInstanceOf(PravoosException.class)
                .hasFieldOrPropertyWithValue("code", "SIGNATURE_NOT_FOUND");
    }

    private SignatureRequest pendingRequest(String hash, LocalDateTime expiresAt) {
        SignatureRequest request = new SignatureRequest();
        setId(request, UUID.randomUUID());
        request.setCaseId(caseId);
        request.setDocumentId(documentId);
        request.setSignerClientId(clientId);
        request.setProvider(SignatureProviderType.SIMPLE);
        request.setStatus(SignatureStatus.PENDING);
        request.setDocumentHash(hash);
        request.setExpiresAt(expiresAt);
        return request;
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
