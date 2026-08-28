package com.pravoos.ai.practice.internal.recyclebin;

import com.pravoos.ai.document.api.DocumentCommand;
import com.pravoos.ai.practice.internal.model.entity.Case;
import com.pravoos.ai.practice.internal.repository.jpa.CasePartyRepository;
import com.pravoos.ai.practice.internal.repository.jpa.CaseRepository;
import com.pravoos.ai.practice.internal.repository.jpa.SignatureRequestRepository;
import com.pravoos.ai.recyclebin.api.BinContents;
import com.pravoos.ai.recyclebin.api.BinSnapshot;
import com.pravoos.ai.recyclebin.api.DeletionActor;
import com.pravoos.ai.recyclebin.api.RecycleBinEntityType;
import com.pravoos.ai.recyclebin.api.SoftDeleteStore;
import com.pravoos.ai.shared.exception.CaseNotFoundException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class CaseSoftDeleteStore implements SoftDeleteStore {

  private final CaseRepository caseRepository;
  private final CasePartyRepository casePartyRepository;
  private final SignatureRequestRepository signatureRequestRepository;
  private final DocumentCommand documentCommand;

  public CaseSoftDeleteStore(
      CaseRepository caseRepository,
      CasePartyRepository casePartyRepository,
      SignatureRequestRepository signatureRequestRepository,
      DocumentCommand documentCommand) {
    this.caseRepository = caseRepository;
    this.casePartyRepository = casePartyRepository;
    this.signatureRequestRepository = signatureRequestRepository;
    this.documentCommand = documentCommand;
  }

  @Override
  public RecycleBinEntityType entityType() {
    return RecycleBinEntityType.CASE;
  }

  @Override
  public BinContents moveToBin(String entityId, DeletionActor actor, boolean cascade) {
    UUID caseId = UUID.fromString(entityId);
    Case caseEntity =
        caseRepository.findById(caseId).orElseThrow(() -> new CaseNotFoundException(caseId));
    BinSnapshot root = snapshot(caseEntity);
    caseRepository.softDelete(caseId, LocalDateTime.now(ZoneOffset.UTC));
    return new BinContents(root, documentCommand.moveCaseDocumentsToBin(caseId));
  }

  @Override
  public void restore(String entityId) {
    caseRepository.restore(UUID.fromString(entityId));
  }

  @Override
  public void purge(String entityId) {
    UUID caseId = UUID.fromString(entityId);
    signatureRequestRepository.deleteByCaseId(caseId);
    documentCommand.purgeByCase(caseId);
    casePartyRepository.deleteByCaseId(caseId);
    caseRepository.hardDelete(caseId);
  }

  static BinSnapshot snapshot(Case caseEntity) {
    Map<String, Object> payload = new HashMap<>();
    payload.put("status", String.valueOf(caseEntity.getStatus()));
    payload.put("createdAt", String.valueOf(caseEntity.getCreatedAt()));
    if (caseEntity.getClientId() != null) {
      payload.put("clientId", caseEntity.getClientId().toString());
    }
    if (caseEntity.getCourtCaseNumber() != null) {
      payload.put("courtCaseNumber", caseEntity.getCourtCaseNumber());
    }
    return new BinSnapshot(
        RecycleBinEntityType.CASE,
        caseEntity.getId().toString(),
        caseEntity.getTitle(),
        caseEntity.getLawyerId(),
        caseEntity.getOrgId(),
        payload);
  }
}
