package com.pravoos.ai.practice.internal.service;

import com.pravoos.ai.practice.internal.dto.CaseMessageResponse;
import com.pravoos.ai.practice.internal.dto.CaseThreadResponse;
import com.pravoos.ai.practice.internal.model.entity.Case;
import com.pravoos.ai.practice.internal.model.entity.CaseMessage;
import com.pravoos.ai.practice.internal.model.entity.CaseThreadRead;
import com.pravoos.ai.practice.internal.repository.jpa.CaseMessageRepository;
import com.pravoos.ai.practice.internal.repository.jpa.CaseThreadReadRepository;
import com.pravoos.ai.shared.event.CaseMessageCreatedKafkaPayload;
import com.pravoos.ai.shared.model.enums.MessageAuthorRole;
import com.pravoos.ai.shared.service.OutboxEventService;
import com.pravoos.ai.shared.util.TextPreview;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CaseMessageService {

  private static final Logger log = LoggerFactory.getLogger(CaseMessageService.class);
  private static final String TOPIC = "case.message.created";
  private static final int PREVIEW_MAX_LENGTH = 140;
  private static final int MAX_THREAD_MESSAGES = 2000;

  private final CaseMessageRepository caseMessageRepository;
  private final CaseThreadReadRepository caseThreadReadRepository;
  private final CaseService caseService;
  private final PortalCaseService portalCaseService;
  private final OutboxEventService outboxEventService;

  public CaseMessageService(
      CaseMessageRepository caseMessageRepository,
      CaseThreadReadRepository caseThreadReadRepository,
      CaseService caseService,
      PortalCaseService portalCaseService,
      OutboxEventService outboxEventService) {
    this.caseMessageRepository = caseMessageRepository;
    this.caseThreadReadRepository = caseThreadReadRepository;
    this.caseService = caseService;
    this.portalCaseService = portalCaseService;
    this.outboxEventService = outboxEventService;
  }

  @Transactional(readOnly = true)
  public List<CaseMessageResponse> findLawyerThread(UUID caseId, UUID lawyerId, List<UUID> orgIds) {
    caseService.requireVisibleCase(caseId, lawyerId, orgIds);
    return toResponses(caseId);
  }

  @Transactional(readOnly = true)
  public List<CaseThreadResponse> findLawyerThreads(UUID lawyerId) {
    return caseMessageRepository.findLawyerThreads(lawyerId).stream()
        .map(CaseThreadResponse::from)
        .toList();
  }

  @Transactional
  public void markThreadRead(UUID caseId, UUID lawyerId, List<UUID> orgIds) {
    caseService.requireVisibleCase(caseId, lawyerId, orgIds);
    CaseThreadRead.Id id = new CaseThreadRead.Id(caseId, lawyerId);
    CaseThreadRead read =
        caseThreadReadRepository
            .findById(id)
            .orElseGet(
                () -> new CaseThreadRead(caseId, lawyerId, LocalDateTime.now(ZoneOffset.UTC)));
    read.setLastReadAt(LocalDateTime.now(ZoneOffset.UTC));
    caseThreadReadRepository.save(read);
  }

  @Transactional
  public CaseMessageResponse postLawyerMessage(
      UUID caseId, String body, UUID lawyerId, List<UUID> orgIds) {
    Case caseEntity = caseService.requireVisibleCase(caseId, lawyerId, orgIds);
    CaseMessage message = save(caseEntity.getId(), lawyerId, MessageAuthorRole.LAWYER, body);
    enqueueNotification(caseEntity, message, MessageAuthorRole.LAWYER);
    log.info("Case message posted by lawyer {} on case {}", lawyerId, caseId);
    return CaseMessageResponse.from(message);
  }

  @Transactional(readOnly = true)
  public List<CaseMessageResponse> findClientThread(UUID caseId, List<UUID> clientIds) {
    portalCaseService.requireClientCase(caseId, clientIds);
    return toResponses(caseId);
  }

  @Transactional
  public CaseMessageResponse postClientMessage(
      UUID caseId, String body, UUID clientUserId, List<UUID> clientIds) {
    Case caseEntity = portalCaseService.requireClientCase(caseId, clientIds);
    CaseMessage message = save(caseEntity.getId(), clientUserId, MessageAuthorRole.CLIENT, body);
    enqueueNotification(caseEntity, message, MessageAuthorRole.CLIENT);
    log.info("Case message posted by client user {} on case {}", clientUserId, caseId);
    return CaseMessageResponse.from(message);
  }

  @Transactional
  public void postSystemMessage(
      Case caseEntity, MessageAuthorRole authorRole, UUID authorUserId, String body) {
    CaseMessage message = save(caseEntity.getId(), authorUserId, authorRole, body);
    enqueueNotification(caseEntity, message, authorRole);
    log.info("System case message posted on case {} as {}", caseEntity.getId(), authorRole);
  }

  private List<CaseMessageResponse> toResponses(UUID caseId) {
    List<CaseMessage> latestFirst =
        caseMessageRepository.findTop2000ByCaseIdOrderByCreatedAtDesc(caseId);
    if (latestFirst.size() == MAX_THREAD_MESSAGES) {
      log.warn(
          "Case {} thread exceeds {} messages; oldest messages truncated from the response",
          caseId,
          MAX_THREAD_MESSAGES);
    }
    return latestFirst.reversed().stream().map(CaseMessageResponse::from).toList();
  }

  private CaseMessage save(
      UUID caseId, UUID authorUserId, MessageAuthorRole authorRole, String body) {
    return caseMessageRepository.save(new CaseMessage(caseId, authorUserId, authorRole, body));
  }

  private void enqueueNotification(
      Case caseEntity, CaseMessage message, MessageAuthorRole authorRole) {
    boolean fromLawyer = authorRole == MessageAuthorRole.LAWYER;
    UUID recipientLawyerId = fromLawyer ? null : caseEntity.getLawyerId();
    UUID recipientClientId = fromLawyer ? caseEntity.getClientId() : null;
    if (recipientLawyerId == null && recipientClientId == null) {
      return;
    }
    CaseMessageCreatedKafkaPayload payload =
        new CaseMessageCreatedKafkaPayload(
            caseEntity.getId(),
            caseEntity.getTitle(),
            message.getId(),
            authorRole.name(),
            message.getAuthorUserId(),
            recipientLawyerId,
            recipientClientId,
            buildPreview(message.getBody()));
    outboxEventService.enqueue(TOPIC, caseEntity.getId().toString(), payload);
  }

  private String buildPreview(String body) {
    return TextPreview.clamp(body.strip(), PREVIEW_MAX_LENGTH);
  }
}
