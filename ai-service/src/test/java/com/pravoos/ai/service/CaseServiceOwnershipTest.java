package com.pravoos.ai.service;

import com.pravoos.ai.exception.CaseNotFoundException;
import com.pravoos.ai.model.entity.Case;
import com.pravoos.ai.repository.jpa.CaseHearingEventRepository;
import com.pravoos.ai.repository.jpa.CaseRepository;
import com.pravoos.ai.repository.jpa.ClientRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CaseServiceOwnershipTest {

    @Mock private CaseRepository caseRepository;
    @Mock private ClientRepository clientRepository;
    @Mock private DocumentService documentService;
    @Mock private CaseHearingEventRepository hearingEventRepository;
    @Mock private ArbitrSyncService arbitrSyncService;

    private CaseService caseService() {
        return new CaseService(caseRepository, clientRepository, documentService,
                hearingEventRepository, arbitrSyncService);
    }

    @Test
    void returnsCaseWhenOwnedByLawyer() {
        UUID caseId = UUID.randomUUID();
        UUID lawyerId = UUID.randomUUID();
        Case owned = new Case();
        owned.setLawyerId(lawyerId);
        when(caseRepository.findById(caseId)).thenReturn(Optional.of(owned));

        assertThat(caseService().requireOwnedCase(caseId, lawyerId)).isSameAs(owned);
    }

    @Test
    void throwsNotFoundWhenOwnedByAnotherLawyer() {
        UUID caseId = UUID.randomUUID();
        Case foreign = new Case();
        foreign.setLawyerId(UUID.randomUUID());
        when(caseRepository.findById(caseId)).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> caseService().requireOwnedCase(caseId, UUID.randomUUID()))
                .isInstanceOf(CaseNotFoundException.class);
    }

    @Test
    void throwsNotFoundWhenCaseMissing() {
        UUID caseId = UUID.randomUUID();
        when(caseRepository.findById(caseId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> caseService().requireOwnedCase(caseId, UUID.randomUUID()))
                .isInstanceOf(CaseNotFoundException.class);
    }
}
