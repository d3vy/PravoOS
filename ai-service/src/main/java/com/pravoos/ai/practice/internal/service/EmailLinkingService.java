package com.pravoos.ai.practice.internal.service;

import com.pravoos.ai.practice.internal.dto.EmailMessageResponse;
import com.pravoos.ai.practice.internal.dto.LinkEmailRequest;
import com.pravoos.ai.practice.internal.model.entity.Case;
import com.pravoos.ai.practice.internal.model.entity.ClientContact;
import com.pravoos.ai.practice.internal.model.entity.EmailMessage;
import com.pravoos.ai.practice.internal.model.entity.Mailbox;
import com.pravoos.ai.practice.internal.repository.jpa.ClientContactRepository;
import com.pravoos.ai.practice.internal.repository.jpa.EmailMessageRepository;
import com.pravoos.ai.practice.internal.repository.jpa.MailboxRepository;
import com.pravoos.ai.practice.internal.service.EmailLinkResolver.EmailLink;
import com.pravoos.ai.practice.internal.service.EmailLinkResolver.LawyerLinkIndex;
import com.pravoos.ai.shared.exception.EmailLinkTargetRequiredException;
import com.pravoos.ai.shared.exception.EmailMessageNotFoundException;
import com.pravoos.ai.shared.exception.MailboxNotFoundException;
import com.pravoos.ai.shared.model.enums.ContactType;
import com.pravoos.ai.shared.model.enums.EmailDirection;
import com.pravoos.ai.shared.model.enums.EmailLinkSource;
import com.pravoos.ai.shared.util.PageRequests;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailLinkingService {

  private static final Logger log = LoggerFactory.getLogger(EmailLinkingService.class);
  private static final int MAX_NOTES_LENGTH = 1000;

  private final EmailMessageRepository emailMessageRepository;
  private final MailboxRepository mailboxRepository;
  private final ClientContactRepository clientContactRepository;
  private final EmailLinkResolver linkResolver;
  private final CaseService caseService;
  private final ClientService clientService;

  public EmailLinkingService(
      EmailMessageRepository emailMessageRepository,
      MailboxRepository mailboxRepository,
      ClientContactRepository clientContactRepository,
      EmailLinkResolver linkResolver,
      CaseService caseService,
      ClientService clientService) {
    this.emailMessageRepository = emailMessageRepository;
    this.mailboxRepository = mailboxRepository;
    this.clientContactRepository = clientContactRepository;
    this.linkResolver = linkResolver;
    this.caseService = caseService;
    this.clientService = clientService;
  }

  @Transactional
  public int autoLinkMailbox(UUID mailboxId) {
    Mailbox mailbox =
        mailboxRepository
            .findById(mailboxId)
            .orElseThrow(() -> new MailboxNotFoundException(mailboxId));
    List<EmailMessage> pending =
        emailMessageRepository.findByMailboxIdAndCaseIdIsNullAndClientIdIsNullOrderBySentAtAsc(
            mailboxId);
    if (pending.isEmpty()) {
      return 0;
    }

    LawyerLinkIndex index = linkResolver.indexFor(mailbox.getUserId());
    int linked = 0;
    for (EmailMessage message : pending) {
      Optional<EmailLink> resolved = linkResolver.resolve(message, mailbox, index);
      if (resolved.isPresent()) {
        applyLink(message, resolved.get());
        linked++;
      }
    }
    log.info(
        "Auto-linked {} of {} unlinked messages in mailbox {}", linked, pending.size(), mailboxId);
    return linked;
  }

  @Transactional(readOnly = true)
  public Page<EmailMessageResponse> findUnlinked(UUID lawyerId, int page, int size) {
    return emailMessageRepository
        .findUnlinkedByUserId(lawyerId, PageRequests.of(page, size))
        .map(EmailMessageResponse::from);
  }

  @Transactional(readOnly = true)
  public List<EmailMessageResponse> findByCase(UUID caseId, UUID lawyerId) {
    caseService.requireOwnedCase(caseId, lawyerId);
    return emailMessageRepository.findByCaseIdOrderBySentAtDesc(caseId).stream()
        .map(EmailMessageResponse::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<EmailMessageResponse> findByClient(UUID clientId, UUID lawyerId) {
    clientService.requireOwnedClient(clientId, lawyerId);
    return emailMessageRepository.findByClientIdOrderBySentAtDesc(clientId).stream()
        .map(EmailMessageResponse::from)
        .toList();
  }

  @Transactional
  public EmailMessageResponse link(UUID messageId, LinkEmailRequest request, UUID lawyerId) {
    if (request.caseId() == null && request.clientId() == null) {
      throw new EmailLinkTargetRequiredException();
    }
    EmailMessage message = requireOwnedMessage(messageId, lawyerId);

    UUID caseId = null;
    UUID clientId = request.clientId();
    boolean clientTakenFromCase = false;
    if (request.caseId() != null) {
      Case caseEntity = caseService.requireOwnedCase(request.caseId(), lawyerId);
      caseId = caseEntity.getId();
      if (caseEntity.getClientId() != null) {
        clientId = caseEntity.getClientId();
        clientTakenFromCase = true;
      }
    }
    if (clientId != null && !clientTakenFromCase) {
      clientService.requireOwnedClient(clientId, lawyerId);
    }

    applyLink(message, new EmailLink(caseId, clientId, EmailLinkSource.MANUAL));
    log.info(
        "Email {} linked to case {} / client {} by lawyer {}",
        messageId,
        caseId,
        clientId,
        lawyerId);
    return EmailMessageResponse.from(message);
  }

  @Transactional
  public EmailMessageResponse unlink(UUID messageId, UUID lawyerId) {
    EmailMessage message = requireOwnedMessage(messageId, lawyerId);
    removeContact(message);
    message.clearLink();
    log.info("Email {} unlinked by lawyer {}", messageId, lawyerId);
    return EmailMessageResponse.from(message);
  }

  private void applyLink(EmailMessage message, EmailLink link) {
    message.applyLink(link.caseId(), link.clientId(), link.source());
    syncContact(message);
  }

  private void syncContact(EmailMessage message) {
    UUID clientId = message.getClientId();
    if (clientId == null) {
      removeContact(message);
      return;
    }
    if (message.getClientContactId() != null) {
      Optional<ClientContact> existing =
          clientContactRepository.findById(message.getClientContactId());
      if (existing.isPresent() && existing.get().getClientId().equals(clientId)) {
        return;
      }
      existing.ifPresent(clientContactRepository::delete);
      message.attachClientContact(null);
    }

    ClientContact contact = new ClientContact();
    contact.setClientId(clientId);
    contact.setType(ContactType.EMAIL);
    contact.setContactDate(contactDate(message));
    contact.setNotes(contactNotes(message));
    message.attachClientContact(clientContactRepository.save(contact).getId());
  }

  private void removeContact(EmailMessage message) {
    if (message.getClientContactId() == null) {
      return;
    }
    clientContactRepository
        .findById(message.getClientContactId())
        .ifPresent(clientContactRepository::delete);
    message.attachClientContact(null);
  }

  private LocalDate contactDate(EmailMessage message) {
    return message.getSentAt() != null
        ? message.getSentAt().toLocalDate()
        : LocalDate.now(ZoneOffset.UTC);
  }

  private String contactNotes(EmailMessage message) {
    String counterparty =
        message.getDirection() == EmailDirection.OUT
            ? message.getToAddresses()
            : message.getFromAddress();
    StringBuilder notes = new StringBuilder();
    notes.append(
        message.getDirection() == EmailDirection.OUT ? "Исходящее письмо" : "Входящее письмо");
    if (counterparty != null && !counterparty.isBlank()) {
      notes.append(message.getDirection() == EmailDirection.OUT ? " для " : " от ");
      notes.append(counterparty.trim());
    }
    if (message.getSubject() != null && !message.getSubject().isBlank()) {
      notes.append(": ").append(message.getSubject().trim());
    }
    String result = notes.toString();
    return result.length() <= MAX_NOTES_LENGTH ? result : result.substring(0, MAX_NOTES_LENGTH);
  }

  private EmailMessage requireOwnedMessage(UUID messageId, UUID lawyerId) {
    return emailMessageRepository
        .findByIdAndUserId(messageId, lawyerId)
        .orElseThrow(() -> new EmailMessageNotFoundException(messageId));
  }
}
