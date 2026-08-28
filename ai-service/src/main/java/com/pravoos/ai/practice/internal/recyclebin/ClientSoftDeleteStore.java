package com.pravoos.ai.practice.internal.recyclebin;

import com.pravoos.ai.practice.internal.model.entity.Case;
import com.pravoos.ai.practice.internal.model.entity.Client;
import com.pravoos.ai.practice.internal.repository.jpa.CaseRepository;
import com.pravoos.ai.practice.internal.repository.jpa.ClientConsentRepository;
import com.pravoos.ai.practice.internal.repository.jpa.ClientRepository;
import com.pravoos.ai.recyclebin.api.BinContents;
import com.pravoos.ai.recyclebin.api.BinSnapshot;
import com.pravoos.ai.recyclebin.api.DeletionActor;
import com.pravoos.ai.recyclebin.api.RecycleBinEntityType;
import com.pravoos.ai.recyclebin.api.SoftDeleteStore;
import com.pravoos.ai.shared.exception.ClientNotFoundException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ClientSoftDeleteStore implements SoftDeleteStore {

  private final ClientRepository clientRepository;
  private final CaseRepository caseRepository;
  private final ClientConsentRepository clientConsentRepository;
  private final CaseSoftDeleteStore caseSoftDeleteStore;

  public ClientSoftDeleteStore(
      ClientRepository clientRepository,
      CaseRepository caseRepository,
      ClientConsentRepository clientConsentRepository,
      CaseSoftDeleteStore caseSoftDeleteStore) {
    this.clientRepository = clientRepository;
    this.caseRepository = caseRepository;
    this.clientConsentRepository = clientConsentRepository;
    this.caseSoftDeleteStore = caseSoftDeleteStore;
  }

  @Override
  public RecycleBinEntityType entityType() {
    return RecycleBinEntityType.CLIENT;
  }

  @Override
  public BinContents moveToBin(String entityId, DeletionActor actor, boolean cascade) {
    UUID clientId = UUID.fromString(entityId);
    Client client =
        clientRepository
            .findById(clientId)
            .orElseThrow(() -> new ClientNotFoundException(clientId));
    BinSnapshot root = snapshot(client);
    LocalDateTime deletedAt = LocalDateTime.now(ZoneOffset.UTC);

    List<BinSnapshot> cascaded = new ArrayList<>();
    if (cascade) {
      List<Case> cases =
          caseRepository.findByClientIdAndLawyerIdOrderByCreatedAtDesc(
              clientId, client.getLawyerId());
      for (Case caseEntity : cases) {
        cascaded.add(CaseSoftDeleteStore.snapshot(caseEntity));
        cascaded.addAll(
            caseSoftDeleteStore.moveToBin(caseEntity.getId().toString(), actor, true).cascaded());
      }
    }
    clientRepository.softDelete(clientId, deletedAt);
    return new BinContents(root, cascaded);
  }

  @Override
  public void restore(String entityId) {
    clientRepository.restore(UUID.fromString(entityId));
  }

  @Override
  public void purge(String entityId) {
    UUID clientId = UUID.fromString(entityId);
    clientConsentRepository.deleteByClientId(clientId);
    clientRepository.hardDelete(clientId);
  }

  private static BinSnapshot snapshot(Client client) {
    Map<String, Object> payload = new HashMap<>();
    payload.put("type", String.valueOf(client.getType()));
    payload.put("createdAt", String.valueOf(client.getCreatedAt()));
    if (client.getEmail() != null) {
      payload.put("email", client.getEmail());
    }
    if (client.getInn() != null) {
      payload.put("inn", client.getInn());
    }
    return new BinSnapshot(
        RecycleBinEntityType.CLIENT,
        client.getId().toString(),
        client.getName(),
        client.getLawyerId(),
        null,
        payload);
  }
}
