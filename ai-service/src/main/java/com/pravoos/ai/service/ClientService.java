package com.pravoos.ai.service;

import com.pravoos.ai.client.UserServiceClient;
import com.pravoos.ai.exception.ClientEmailRequiredException;
import com.pravoos.ai.exception.ClientNotFoundException;
import com.pravoos.ai.model.dto.*;
import com.pravoos.ai.model.entity.Case;
import com.pravoos.ai.model.entity.Client;
import com.pravoos.ai.model.enums.ClientType;
import com.pravoos.ai.repository.jpa.CaseRepository;
import com.pravoos.ai.repository.jpa.ClientRepository;
import com.pravoos.ai.util.PageRequests;
import com.pravoos.common.util.PhoneNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ClientService {

    private static final Logger log = LoggerFactory.getLogger(ClientService.class);

    private final ClientRepository clientRepository;
    private final CaseRepository caseRepository;
    private final CaseService caseService;
    private final UserServiceClient userServiceClient;

    public ClientService(ClientRepository clientRepository,
                         CaseRepository caseRepository,
                         CaseService caseService,
                         UserServiceClient userServiceClient) {
        this.clientRepository = clientRepository;
        this.caseRepository = caseRepository;
        this.caseService = caseService;
        this.userServiceClient = userServiceClient;
    }

    @Transactional(readOnly = true)
    public void invitePortal(UUID clientId, UUID lawyerId) {
        Client client = requireOwnedClient(clientId, lawyerId);
        if (client.getEmail() == null || client.getEmail().isBlank()) {
            throw new ClientEmailRequiredException(clientId);
        }
        userServiceClient.createPortalInvite(clientId, lawyerId, client.getEmail(), client.getName());
        log.info("Portal invite requested for client {} by lawyer {}", clientId, lawyerId);
    }

    @Transactional(readOnly = true)
    public PortalInviteStatusResponse portalInviteStatus(UUID clientId, UUID lawyerId) {
        requireOwnedClient(clientId, lawyerId);
        return userServiceClient.getPortalInviteStatus(clientId);
    }

    @Transactional(readOnly = true)
    public void revokePortalInvite(UUID clientId, UUID lawyerId) {
        requireOwnedClient(clientId, lawyerId);
        userServiceClient.revokePortalInvite(clientId);
        log.info("Portal invite revoked for client {} by lawyer {}", clientId, lawyerId);
    }

    @Transactional
    public ClientResponse create(CreateClientRequest request, UUID lawyerId) {
        Client client = new Client();
        client.setLawyerId(lawyerId);
        applyRequest(client, request.name(), request.type(), request.phone(),
                request.email(), request.inn(), request.notes());

        Client saved = clientRepository.save(client);
        log.info("Client created: '{}' ({}) by lawyer {}", saved.getName(), saved.getId(), lawyerId);
        return ClientResponse.from(saved, 0L);
    }

    @Transactional(readOnly = true)
    public Page<ClientResponse> findByLawyer(UUID lawyerId, int page, int size) {
        Map<UUID, Long> caseCounts = caseCountsFor(lawyerId);
        Page<Client> clients = clientRepository
                .findByLawyerIdOrderByCreatedAtDesc(lawyerId, PageRequests.of(page, size));
        return clients.map(client -> ClientResponse.from(client, caseCounts.getOrDefault(client.getId(), 0L)));
    }

    @Transactional(readOnly = true)
    public ClientDetailResponse get(UUID clientId, UUID lawyerId) {
        Client client = requireOwnedClient(clientId, lawyerId);
        List<CaseResponse> cases = caseRepository
                .findByClientIdAndLawyerIdOrderByCreatedAtDesc(clientId, lawyerId)
                .stream()
                .map(caseEntity -> CaseResponse.from(caseEntity, client.getName()))
                .toList();
        return new ClientDetailResponse(ClientResponse.from(client, cases.size()), cases);
    }

    @Transactional
    public ClientResponse update(UUID clientId, UpdateClientRequest request, UUID lawyerId) {
        Client client = requireOwnedClient(clientId, lawyerId);
        applyRequest(client, request.name(), request.type(), request.phone(),
                request.email(), request.inn(), request.notes());

        long caseCount = caseRepository.findByClientIdAndLawyerIdOrderByCreatedAtDesc(clientId, lawyerId).size();
        log.info("Client updated: {} by lawyer {}", clientId, lawyerId);
        return ClientResponse.from(client, caseCount);
    }

    @Transactional
    public void delete(UUID clientId, UUID lawyerId, boolean cascade) {
        Client client = requireOwnedClient(clientId, lawyerId);

        if (cascade) {
            List<Case> cases = caseRepository.findByClientIdAndLawyerIdOrderByCreatedAtDesc(clientId, lawyerId);
            for (Case caseEntity : cases) {
                caseService.delete(caseEntity.getId(), lawyerId);
            }
            log.info("Cascade-deleted {} cases for client {} by lawyer {}", cases.size(), clientId, lawyerId);
        }

        clientRepository.delete(client);
        log.info("Client deleted: {} (cascade={}) by lawyer {}", clientId, cascade, lawyerId);
    }

    public Client requireOwnedClient(UUID clientId, UUID lawyerId) {
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new ClientNotFoundException(clientId));
        if (!client.getLawyerId().equals(lawyerId)) {
            log.warn("Lawyer {} attempted to access client {} owned by another user", lawyerId, clientId);
            throw new ClientNotFoundException(clientId);
        }
        return client;
    }

    private void applyRequest(Client client, String name, ClientType type,
                              String phone, String email, String inn, String notes) {
        client.setName(name.trim());
        client.setType(type);
        client.setPhone(normalizePhone(phone));
        client.setEmail(normalizeEmail(email));
        client.setInn(blankToNull(inn));
        client.setNotes(blankToNull(notes));
    }

    private Map<UUID, Long> caseCountsFor(UUID lawyerId) {
        return caseRepository.findByLawyerIdOrderByCreatedAtDesc(lawyerId)
                .stream()
                .filter(caseEntity -> caseEntity.getClientId() != null)
                .collect(Collectors.groupingBy(Case::getClientId, Collectors.counting()));
    }

    private String normalizePhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        return PhoneNormalizer.normalize(phone);
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return email.trim().toLowerCase();
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
