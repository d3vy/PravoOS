package com.pravoos.ai.practice.internal.service;

import com.pravoos.ai.practice.internal.model.entity.Client;
import com.pravoos.ai.practice.internal.repository.jpa.CaseRepository;
import com.pravoos.ai.practice.internal.repository.jpa.ClientRepository;
import com.pravoos.ai.shared.client.UserServiceClient;
import com.pravoos.ai.shared.exception.ClientNotFoundException;
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
class ClientServiceOwnershipTest {

    @Mock private ClientRepository clientRepository;
    @Mock private CaseRepository caseRepository;
    @Mock private CaseService caseService;
    @Mock private UserServiceClient userServiceClient;

    private ClientService clientService() {
        return new ClientService(clientRepository, caseRepository, caseService, userServiceClient);
    }

    @Test
    void returnsClientWhenOwnedByLawyer() {
        UUID clientId = UUID.randomUUID();
        UUID lawyerId = UUID.randomUUID();
        Client owned = new Client();
        owned.setLawyerId(lawyerId);
        when(clientRepository.findById(clientId)).thenReturn(Optional.of(owned));

        assertThat(clientService().requireOwnedClient(clientId, lawyerId)).isSameAs(owned);
    }

    @Test
    void throwsNotFoundWhenOwnedByAnotherLawyer() {
        UUID clientId = UUID.randomUUID();
        Client foreign = new Client();
        foreign.setLawyerId(UUID.randomUUID());
        when(clientRepository.findById(clientId)).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> clientService().requireOwnedClient(clientId, UUID.randomUUID()))
                .isInstanceOf(ClientNotFoundException.class);
    }

    @Test
    void throwsNotFoundWhenClientMissing() {
        UUID clientId = UUID.randomUUID();
        when(clientRepository.findById(clientId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> clientService().requireOwnedClient(clientId, UUID.randomUUID()))
                .isInstanceOf(ClientNotFoundException.class);
    }
}
