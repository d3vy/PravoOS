package com.pravoos.ai.practice.internal.service;

import com.pravoos.ai.core.internal.service.DocumentService;

import com.pravoos.ai.client.UserServiceClient;
import com.pravoos.ai.exception.CaseNotFoundException;
import com.pravoos.ai.exception.OrganizationAccessException;
import com.pravoos.ai.model.dto.CaseResponse;
import com.pravoos.ai.model.dto.CreateCaseRequest;
import com.pravoos.ai.practice.internal.model.entity.Case;
import com.pravoos.ai.practice.internal.repository.jpa.CaseHearingEventRepository;
import com.pravoos.ai.practice.internal.repository.jpa.CaseRepository;
import com.pravoos.ai.practice.internal.repository.jpa.ClientRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CaseServiceVisibilityTest {

    @Mock private CaseRepository caseRepository;
    @Mock private ClientRepository clientRepository;
    @Mock private DocumentService documentService;
    @Mock private CaseHearingEventRepository hearingEventRepository;
    @Mock private ArbitrSyncService arbitrSyncService;
    @Mock private UserServiceClient userServiceClient;

    private CaseService caseService() {
        return new CaseService(caseRepository, clientRepository, documentService,
                hearingEventRepository, arbitrSyncService, userServiceClient);
    }

    @Test
    void visibleWhenOwnedByLawyer() {
        UUID caseId = UUID.randomUUID();
        UUID lawyerId = UUID.randomUUID();
        Case owned = new Case();
        owned.setLawyerId(lawyerId);
        when(caseRepository.findById(caseId)).thenReturn(Optional.of(owned));

        assertThat(caseService().requireVisibleCase(caseId, lawyerId, List.of())).isSameAs(owned);
    }

    @Test
    void visibleWhenCaseOrgInCallerOrgs() {
        UUID caseId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        Case shared = new Case();
        shared.setLawyerId(UUID.randomUUID());
        shared.setOrgId(orgId);
        when(caseRepository.findById(caseId)).thenReturn(Optional.of(shared));

        assertThat(caseService().requireVisibleCase(caseId, UUID.randomUUID(), List.of(orgId))).isSameAs(shared);
    }

    @Test
    void notVisibleWhenForeignAndOrgNotShared() {
        UUID caseId = UUID.randomUUID();
        Case foreign = new Case();
        foreign.setLawyerId(UUID.randomUUID());
        foreign.setOrgId(UUID.randomUUID());
        when(caseRepository.findById(caseId)).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> caseService().requireVisibleCase(caseId, UUID.randomUUID(), List.of(UUID.randomUUID())))
                .isInstanceOf(CaseNotFoundException.class);
    }

    @Test
    void notVisibleWhenPersonalCaseOfAnotherLawyer() {
        UUID caseId = UUID.randomUUID();
        Case personal = new Case();
        personal.setLawyerId(UUID.randomUUID());
        when(caseRepository.findById(caseId)).thenReturn(Optional.of(personal));

        assertThatThrownBy(() -> caseService().requireVisibleCase(caseId, UUID.randomUUID(), List.of(UUID.randomUUID())))
                .isInstanceOf(CaseNotFoundException.class);
    }

    @Test
    void createRejectsOrgNotInCallerMemberships() {
        UUID lawyerId = UUID.randomUUID();
        CreateCaseRequest request = new CreateCaseRequest("Дело", null, null, UUID.randomUUID(),
                null, null, null, null);

        assertThatThrownBy(() -> caseService().create(request, lawyerId, List.of(UUID.randomUUID())))
                .isInstanceOf(OrganizationAccessException.class);
    }

    @Test
    void createStampsOrgWhenLawyerIsMember() {
        UUID lawyerId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        CreateCaseRequest request = new CreateCaseRequest("Дело", null, null, orgId,
                null, null, null, null);
        when(caseRepository.save(any(Case.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CaseResponse response = caseService().create(request, lawyerId, List.of(orgId));

        assertThat(response.orgId()).isEqualTo(orgId);
        assertThat(response.ownerId()).isEqualTo(lawyerId);
    }
}
