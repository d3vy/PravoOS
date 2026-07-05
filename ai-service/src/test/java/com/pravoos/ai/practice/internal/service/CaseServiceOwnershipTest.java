package com.pravoos.ai.practice.internal.service;

import com.pravoos.ai.core.api.DocumentCommand;
import com.pravoos.ai.core.api.DocumentQuery;

import com.pravoos.ai.shared.client.UserServiceClient;
import com.pravoos.ai.shared.exception.CaseNotFoundException;
import com.pravoos.ai.shared.exception.CaseTransferNotAllowedException;
import com.pravoos.ai.shared.exception.OrganizationAccessException;
import com.pravoos.ai.practice.internal.dto.CaseResponse;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CaseServiceOwnershipTest {

    @Mock private CaseRepository caseRepository;
    @Mock private ClientRepository clientRepository;
    @Mock private DocumentCommand documentCommand;
    @Mock private DocumentQuery documentQuery;
    @Mock private CaseHearingEventRepository hearingEventRepository;
    @Mock private ArbitrSyncService arbitrSyncService;
    @Mock private UserServiceClient userServiceClient;

    private CaseService caseService() {
        return new CaseService(caseRepository, clientRepository, documentCommand, documentQuery,
                hearingEventRepository, arbitrSyncService, userServiceClient);
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

    @Test
    void changeOrgMovesCaseIntoCallerOrg() {
        UUID caseId = UUID.randomUUID();
        UUID lawyerId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        Case owned = new Case();
        owned.setLawyerId(lawyerId);
        when(caseRepository.findById(caseId)).thenReturn(Optional.of(owned));

        CaseResponse response = caseService().changeOrg(caseId, orgId, lawyerId, List.of(orgId));

        assertThat(response.orgId()).isEqualTo(orgId);
        assertThat(owned.getOrgId()).isEqualTo(orgId);
    }

    @Test
    void changeOrgToNullMakesCasePersonal() {
        UUID caseId = UUID.randomUUID();
        UUID lawyerId = UUID.randomUUID();
        Case owned = new Case();
        owned.setLawyerId(lawyerId);
        owned.setOrgId(UUID.randomUUID());
        when(caseRepository.findById(caseId)).thenReturn(Optional.of(owned));

        CaseResponse response = caseService().changeOrg(caseId, null, lawyerId, List.of(UUID.randomUUID()));

        assertThat(response.orgId()).isNull();
        assertThat(owned.getOrgId()).isNull();
    }

    @Test
    void changeOrgRejectsForeignOrg() {
        UUID caseId = UUID.randomUUID();
        UUID lawyerId = UUID.randomUUID();
        Case owned = new Case();
        owned.setLawyerId(lawyerId);
        when(caseRepository.findById(caseId)).thenReturn(Optional.of(owned));

        assertThatThrownBy(() -> caseService().changeOrg(caseId, UUID.randomUUID(), lawyerId, List.of()))
                .isInstanceOf(OrganizationAccessException.class);
    }

    @Test
    void transferOwnerReassignsOrgCase() {
        UUID caseId = UUID.randomUUID();
        UUID lawyerId = UUID.randomUUID();
        UUID newOwnerId = UUID.randomUUID();
        Case owned = new Case();
        owned.setLawyerId(lawyerId);
        UUID orgId = UUID.randomUUID();
        owned.setOrgId(orgId);
        when(caseRepository.findById(caseId)).thenReturn(Optional.of(owned));
        when(userServiceClient.isOrgMember(orgId, newOwnerId)).thenReturn(true);

        CaseResponse response = caseService().transferOwner(caseId, newOwnerId, lawyerId);

        assertThat(response.ownerId()).isEqualTo(newOwnerId);
        assertThat(owned.getLawyerId()).isEqualTo(newOwnerId);
    }

    @Test
    void transferOwnerRejectsNonMember() {
        UUID caseId = UUID.randomUUID();
        UUID lawyerId = UUID.randomUUID();
        UUID newOwnerId = UUID.randomUUID();
        Case owned = new Case();
        owned.setLawyerId(lawyerId);
        UUID orgId = UUID.randomUUID();
        owned.setOrgId(orgId);
        when(caseRepository.findById(caseId)).thenReturn(Optional.of(owned));
        when(userServiceClient.isOrgMember(orgId, newOwnerId)).thenReturn(false);

        assertThatThrownBy(() -> caseService().transferOwner(caseId, newOwnerId, lawyerId))
                .isInstanceOf(CaseTransferNotAllowedException.class);
        assertThat(owned.getLawyerId()).isEqualTo(lawyerId);
    }

    @Test
    void transferOwnerRejectsPersonalCase() {
        UUID caseId = UUID.randomUUID();
        UUID lawyerId = UUID.randomUUID();
        Case owned = new Case();
        owned.setLawyerId(lawyerId);
        when(caseRepository.findById(caseId)).thenReturn(Optional.of(owned));

        assertThatThrownBy(() -> caseService().transferOwner(caseId, UUID.randomUUID(), lawyerId))
                .isInstanceOf(CaseTransferNotAllowedException.class);
    }
}
