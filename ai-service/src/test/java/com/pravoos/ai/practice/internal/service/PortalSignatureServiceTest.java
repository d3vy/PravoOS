package com.pravoos.ai.practice.internal.service;

import com.pravoos.ai.practice.internal.dto.SignDocumentRequest;
import com.pravoos.ai.practice.internal.dto.SignerContext;
import com.pravoos.ai.practice.internal.model.entity.Case;
import com.pravoos.ai.practice.internal.model.entity.SignatureRequest;
import com.pravoos.ai.practice.internal.repository.jpa.CaseHearingEventRepository;
import com.pravoos.ai.practice.internal.repository.jpa.CaseRepository;
import com.pravoos.ai.practice.internal.repository.jpa.SignatureRequestRepository;
import com.pravoos.ai.shared.exception.CaseNotFoundException;
import com.pravoos.ai.shared.exception.PravoosException;
import com.pravoos.ai.shared.model.enums.SignatureProviderType;
import com.pravoos.ai.shared.model.enums.SignatureStatus;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PortalSignatureServiceTest {

    @Mock private SignatureRequestRepository signatureRequestRepository;
    @Mock private CaseRepository caseRepository;
    @Mock private CaseHearingEventRepository hearingEventRepository;
    @Mock private SignatureService signatureService;

    private PortalSignatureService service;

    private final UUID caseId = UUID.randomUUID();
    private final UUID clientId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        PortalCaseService portalCaseService = new PortalCaseService(caseRepository, hearingEventRepository);
        service = new PortalSignatureService(signatureRequestRepository, portalCaseService, signatureService);
    }

    private SignatureRequest request(SignatureStatus status, LocalDateTime expiresAt) {
        SignatureRequest request = new SignatureRequest();
        request.setCaseId(caseId);
        request.setSignerClientId(clientId);
        request.setProvider(SignatureProviderType.SIMPLE);
        request.setStatus(status);
        request.setDocumentHash("hash");
        request.setExpiresAt(expiresAt);
        return request;
    }

    @Test
    void listPending_dropsExpiredRequests() {
        SignatureRequest live = request(SignatureStatus.PENDING, LocalDateTime.now(ZoneOffset.UTC).plusDays(2));
        SignatureRequest expired = request(SignatureStatus.PENDING, LocalDateTime.now(ZoneOffset.UTC).minusDays(2));
        when(signatureRequestRepository.findBySignerClientIdInAndStatusOrderByCreatedAtDesc(
                List.of(clientId), SignatureStatus.PENDING)).thenReturn(List.of(live, expired));

        assertThat(service.listPending(List.of(clientId))).hasSize(1);
    }

    @Test
    void listPending_returnsEmptyForNoClients() {
        assertThat(service.listPending(List.of())).isEmpty();
        verifyNoInteractions(signatureRequestRepository);
    }

    @Test
    void sign_delegatesAfterClientAccessCheck() {
        SignatureRequest pending = request(SignatureStatus.PENDING, LocalDateTime.now(ZoneOffset.UTC).plusDays(2));
        UUID signatureId = UUID.randomUUID();
        when(signatureRequestRepository.findById(signatureId)).thenReturn(Optional.of(pending));
        Case caseEntity = new Case();
        caseEntity.setClientId(clientId);
        when(caseRepository.findById(caseId)).thenReturn(Optional.of(caseEntity));

        SignDocumentRequest signRequest = new SignDocumentRequest("Пётр Петров", true);
        SignerContext signer = new SignerContext(UUID.randomUUID(), "ip", "ua");
        service.sign(signatureId, signRequest, List.of(clientId), signer);

        verify(signatureService).sign(eq(caseEntity), eq(signatureId), eq(signRequest), eq(signer));
    }

    @Test
    void sign_rejectsSignatureOutsideClientScope() {
        SignatureRequest pending = request(SignatureStatus.PENDING, LocalDateTime.now(ZoneOffset.UTC).plusDays(2));
        UUID signatureId = UUID.randomUUID();
        when(signatureRequestRepository.findById(signatureId)).thenReturn(Optional.of(pending));
        Case caseEntity = new Case();
        caseEntity.setClientId(clientId);
        when(caseRepository.findById(caseId)).thenReturn(Optional.of(caseEntity));

        assertThatThrownBy(() -> service.sign(signatureId, new SignDocumentRequest("Пётр", true),
                List.of(UUID.randomUUID()), new SignerContext(UUID.randomUUID(), "ip", "ua")))
                .isInstanceOf(CaseNotFoundException.class);
        verify(signatureService, never()).sign(any(), any(), any(), any());
    }

    @Test
    void sign_rejectsUnknownSignature() {
        UUID signatureId = UUID.randomUUID();
        when(signatureRequestRepository.findById(signatureId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.sign(signatureId, new SignDocumentRequest("Пётр", true),
                List.of(clientId), new SignerContext(UUID.randomUUID(), "ip", "ua")))
                .isInstanceOf(PravoosException.class)
                .hasFieldOrPropertyWithValue("code", "SIGNATURE_NOT_FOUND");
    }
}
