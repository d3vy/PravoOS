package com.pravoos.ai.practice.internal.service;

import com.pravoos.ai.practice.internal.dto.*;
import com.pravoos.ai.practice.internal.model.entity.Client;
import com.pravoos.ai.practice.internal.model.entity.ClientConsent;
import com.pravoos.ai.practice.internal.repository.jpa.CaseRepository;
import com.pravoos.ai.practice.internal.repository.jpa.ClientConsentRepository;
import com.pravoos.ai.practice.internal.repository.jpa.ClientRepository;
import com.pravoos.ai.practice.internal.repository.jpa.InvoiceRepository;
import com.pravoos.ai.recyclebin.api.DeletionActor;
import com.pravoos.ai.recyclebin.api.RecycleBin;
import com.pravoos.ai.recyclebin.api.RecycleBinEntityType;
import com.pravoos.ai.shared.client.UserServiceClient;
import com.pravoos.ai.shared.config.PersonalDataConsentProperties;
import com.pravoos.ai.shared.dto.PortalInviteStatusResponse;
import com.pravoos.ai.shared.exception.ClientEmailRequiredException;
import com.pravoos.ai.shared.exception.ClientHasIssuedInvoicesException;
import com.pravoos.ai.shared.exception.ClientNotFoundException;
import com.pravoos.ai.shared.model.enums.ClientType;
import com.pravoos.ai.shared.model.enums.InvoiceStatus;
import com.pravoos.ai.shared.util.PageRequests;
import com.pravoos.common.util.PhoneNormalizer;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClientService {

  private static final Logger log = LoggerFactory.getLogger(ClientService.class);

  private final ClientRepository clientRepository;
  private final ClientConsentRepository clientConsentRepository;
  private final CaseRepository caseRepository;
  private final InvoiceRepository invoiceRepository;
  private final CaseService caseService;
  private final UserServiceClient userServiceClient;
  private final PersonalDataConsentProperties consentProperties;
  private final RecycleBin recycleBin;

  public ClientService(
      ClientRepository clientRepository,
      ClientConsentRepository clientConsentRepository,
      CaseRepository caseRepository,
      InvoiceRepository invoiceRepository,
      CaseService caseService,
      UserServiceClient userServiceClient,
      PersonalDataConsentProperties consentProperties,
      RecycleBin recycleBin) {
    this.clientRepository = clientRepository;
    this.clientConsentRepository = clientConsentRepository;
    this.caseRepository = caseRepository;
    this.invoiceRepository = invoiceRepository;
    this.caseService = caseService;
    this.userServiceClient = userServiceClient;
    this.consentProperties = consentProperties;
    this.recycleBin = recycleBin;
  }

  public void invitePortal(UUID clientId, UUID lawyerId) {
    Client client = requireOwnedClient(clientId, lawyerId);
    if (client.getEmail() == null || client.getEmail().isBlank()) {
      throw new ClientEmailRequiredException(clientId);
    }
    userServiceClient.createPortalInvite(clientId, lawyerId, client.getEmail(), client.getName());
    log.info("Portal invite requested for client {} by lawyer {}", clientId, lawyerId);
  }

  public PortalInviteStatusResponse portalInviteStatus(UUID clientId, UUID lawyerId) {
    requireOwnedClient(clientId, lawyerId);
    return userServiceClient.getPortalInviteStatus(clientId);
  }

  public void revokePortalInvite(UUID clientId, UUID lawyerId) {
    requireOwnedClient(clientId, lawyerId);
    userServiceClient.revokePortalInvite(clientId);
    log.info("Portal invite revoked for client {} by lawyer {}", clientId, lawyerId);
  }

  @Transactional
  public ClientResponse create(CreateClientRequest request, UUID lawyerId) {
    Client client = new Client();
    client.setLawyerId(lawyerId);
    applyRequest(
        client,
        request.name(),
        request.type(),
        request.phone(),
        request.email(),
        request.inn(),
        request.notes());

    Client saved = clientRepository.save(client);
    recordConsent(saved.getId(), lawyerId);
    log.info("Client created: '{}' ({}) by lawyer {}", saved.getName(), saved.getId(), lawyerId);
    return ClientResponse.from(saved, 0L);
  }

  @Transactional(readOnly = true)
  public ConsentResponse currentConsent(UUID clientId, UUID lawyerId) {
    requireOwnedClient(clientId, lawyerId);
    return clientConsentRepository
        .findFirstByClientIdOrderByGrantedAtDesc(clientId)
        .map(ConsentResponse::from)
        .orElse(null);
  }

  @Transactional
  public ConsentResponse grantConsent(UUID clientId, UUID lawyerId) {
    requireOwnedClient(clientId, lawyerId);
    clientConsentRepository
        .findFirstByClientIdOrderByGrantedAtDesc(clientId)
        .filter(ClientConsent::isActive)
        .ifPresent(ClientConsent::revoke);
    ConsentResponse response = ConsentResponse.from(recordConsent(clientId, lawyerId));
    log.info(
        "Consent granted (v{}) for client {} by lawyer {}",
        response.policyVersion(),
        clientId,
        lawyerId);
    return response;
  }

  @Transactional
  public void revokeConsent(UUID clientId, UUID lawyerId) {
    requireOwnedClient(clientId, lawyerId);
    clientConsentRepository
        .findFirstByClientIdOrderByGrantedAtDesc(clientId)
        .filter(ClientConsent::isActive)
        .ifPresent(
            consent -> {
              consent.revoke();
              log.info("Consent revoked for client {} by lawyer {}", clientId, lawyerId);
            });
  }

  @Transactional(readOnly = true)
  public PersonalDataExportResponse exportPersonalData(UUID clientId, UUID lawyerId) {
    Client client = requireOwnedClient(clientId, lawyerId);
    List<ConsentResponse> consents =
        clientConsentRepository.findByClientIdOrderByGrantedAtDesc(clientId).stream()
            .map(ConsentResponse::from)
            .toList();
    List<CaseResponse> cases =
        caseRepository.findByClientIdAndLawyerIdOrderByCreatedAtDesc(clientId, lawyerId).stream()
            .map(caseEntity -> CaseResponse.from(caseEntity, client.getName()))
            .toList();
    log.info("Personal data export produced for client {} by lawyer {}", clientId, lawyerId);
    return PersonalDataExportResponse.of(client, consents, cases);
  }

  private ClientConsent recordConsent(UUID clientId, UUID lawyerId) {
    ClientConsent consent = new ClientConsent();
    consent.setClientId(clientId);
    consent.setLawyerId(lawyerId);
    consent.setPolicyVersion(consentProperties.resolvedVersion());
    return clientConsentRepository.save(consent);
  }

  @Transactional(readOnly = true)
  public Page<ClientResponse> findByLawyer(UUID lawyerId, int page, int size) {
    Map<UUID, Long> caseCounts = caseCountsFor(lawyerId);
    Page<Client> clients =
        clientRepository.findByLawyerIdOrderByCreatedAtDesc(lawyerId, PageRequests.of(page, size));
    return clients.map(
        client -> ClientResponse.from(client, caseCounts.getOrDefault(client.getId(), 0L)));
  }

  @Transactional(readOnly = true)
  public ClientDetailResponse get(UUID clientId, UUID lawyerId) {
    Client client = requireOwnedClient(clientId, lawyerId);
    List<CaseResponse> cases =
        caseRepository.findByClientIdAndLawyerIdOrderByCreatedAtDesc(clientId, lawyerId).stream()
            .map(caseEntity -> CaseResponse.from(caseEntity, client.getName()))
            .toList();
    return new ClientDetailResponse(ClientResponse.from(client, cases.size()), cases);
  }

  @Transactional
  public ClientResponse update(UUID clientId, UpdateClientRequest request, UUID lawyerId) {
    Client client = requireOwnedClient(clientId, lawyerId);
    applyRequest(
        client,
        request.name(),
        request.type(),
        request.phone(),
        request.email(),
        request.inn(),
        request.notes());

    long caseCount = caseRepository.countByClientIdAndLawyerId(clientId, lawyerId);
    log.info("Client updated: {} by lawyer {}", clientId, lawyerId);
    return ClientResponse.from(client, caseCount);
  }

  @Transactional
  public void delete(UUID clientId, DeletionActor actor, boolean cascade) {
    requireOwnedClient(clientId, actor.userId());
    requireNoIssuedInvoices(clientId);
    recycleBin.moveToBin(RecycleBinEntityType.CLIENT, clientId.toString(), actor, cascade);
  }

  private void requireNoIssuedInvoices(UUID clientId) {
    long issuedInvoices =
        invoiceRepository.countByClientIdAndStatusNot(clientId, InvoiceStatus.DRAFT);
    if (issuedInvoices > 0) {
      log.warn(
          "Client {} deletion refused: {} non-draft invoice(s) would be destroyed",
          clientId,
          issuedInvoices);
      throw new ClientHasIssuedInvoicesException(clientId, issuedInvoices);
    }
  }

  public Client requireOwnedClient(UUID clientId, UUID lawyerId) {
    Client client =
        clientRepository
            .findById(clientId)
            .orElseThrow(() -> new ClientNotFoundException(clientId));
    if (!client.getLawyerId().equals(lawyerId)) {
      log.warn("Lawyer {} attempted to access client {} owned by another user", lawyerId, clientId);
      throw new ClientNotFoundException(clientId);
    }
    return client;
  }

  private void applyRequest(
      Client client,
      String name,
      ClientType type,
      String phone,
      String email,
      String inn,
      String notes) {
    client.setName(name.trim());
    client.setType(type);
    client.setPhone(normalizePhone(phone));
    client.setEmail(normalizeEmail(email));
    client.setInn(blankToNull(inn));
    client.setNotes(blankToNull(notes));
  }

  private Map<UUID, Long> caseCountsFor(UUID lawyerId) {
    return caseRepository.countGroupedByClientId(lawyerId).stream()
        .collect(
            Collectors.toMap(
                CaseRepository.ClientCountView::getClientId,
                CaseRepository.ClientCountView::getCount));
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
