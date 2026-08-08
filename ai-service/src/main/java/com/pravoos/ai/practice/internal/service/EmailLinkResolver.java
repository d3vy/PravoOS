package com.pravoos.ai.practice.internal.service;

import com.pravoos.ai.court.api.CourtCaseNumberParser;
import com.pravoos.ai.practice.internal.model.entity.Case;
import com.pravoos.ai.practice.internal.model.entity.Client;
import com.pravoos.ai.practice.internal.model.entity.EmailMessage;
import com.pravoos.ai.practice.internal.model.entity.Mailbox;
import com.pravoos.ai.practice.internal.repository.jpa.CaseRepository;
import com.pravoos.ai.practice.internal.repository.jpa.ClientRepository;
import com.pravoos.ai.practice.internal.repository.jpa.EmailMessageRepository;
import com.pravoos.ai.shared.mail.EmailAddresses;
import com.pravoos.ai.shared.model.enums.EmailLinkSource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Component
public class EmailLinkResolver {

  public record EmailLink(UUID caseId, UUID clientId, EmailLinkSource source) {}

  public record CaseRef(UUID caseId, UUID clientId) {}

  public record LawyerLinkIndex(
      Map<String, CaseRef> casesByNumber, Map<String, UUID> clientsByEmail) {}

  private static final Pageable THREAD_SIBLING_PAGE = PageRequest.of(0, 2);

  private final CaseRepository caseRepository;
  private final ClientRepository clientRepository;
  private final EmailMessageRepository emailMessageRepository;

  public EmailLinkResolver(
      CaseRepository caseRepository,
      ClientRepository clientRepository,
      EmailMessageRepository emailMessageRepository) {
    this.caseRepository = caseRepository;
    this.clientRepository = clientRepository;
    this.emailMessageRepository = emailMessageRepository;
  }

  public LawyerLinkIndex indexFor(UUID lawyerId) {
    Map<String, CaseRef> casesByNumber = new LinkedHashMap<>();
    for (Case caseEntity : caseRepository.findByLawyerIdAndCourtCaseNumberIsNotNull(lawyerId)) {
      String canonical = CourtCaseNumberParser.canonical(caseEntity.getCourtCaseNumber());
      if (canonical != null) {
        casesByNumber.putIfAbsent(
            canonical, new CaseRef(caseEntity.getId(), caseEntity.getClientId()));
      }
    }

    Map<String, UUID> clientsByEmail = new LinkedHashMap<>();
    for (Client client : clientRepository.findByLawyerIdOrderByCreatedAtDesc(lawyerId)) {
      for (String address : EmailAddresses.extractAll(client.getEmail())) {
        clientsByEmail.putIfAbsent(address, client.getId());
      }
    }

    return new LawyerLinkIndex(Map.copyOf(casesByNumber), Map.copyOf(clientsByEmail));
  }

  public Optional<EmailLink> resolve(EmailMessage message, Mailbox mailbox, LawyerLinkIndex index) {
    return resolveByCaseNumber(message, index)
        .or(() -> resolveByThread(message))
        .or(() -> resolveByAddress(message, mailbox, index));
  }

  private Optional<EmailLink> resolveByCaseNumber(EmailMessage message, LawyerLinkIndex index) {
    if (index.casesByNumber().isEmpty()) {
      return Optional.empty();
    }
    for (String caseNumber : CourtCaseNumberParser.extractAll(message.getSubject())) {
      CaseRef match = index.casesByNumber().get(CourtCaseNumberParser.canonical(caseNumber));
      if (match != null) {
        return Optional.of(
            new EmailLink(match.caseId(), match.clientId(), EmailLinkSource.CASE_NUMBER));
      }
    }
    return Optional.empty();
  }

  private Optional<EmailLink> resolveByThread(EmailMessage message) {
    if (message.getThreadKey() == null || message.getThreadKey().isBlank()) {
      return Optional.empty();
    }
    List<EmailMessage> linkedInThread =
        emailMessageRepository.findLinkedInThread(
            message.getMailboxId(), message.getThreadKey(), THREAD_SIBLING_PAGE);
    return linkedInThread.stream()
        .filter(sibling -> message.getId() == null || !message.getId().equals(sibling.getId()))
        .findFirst()
        .map(
            sibling ->
                new EmailLink(sibling.getCaseId(), sibling.getClientId(), EmailLinkSource.THREAD));
  }

  private Optional<EmailLink> resolveByAddress(
      EmailMessage message, Mailbox mailbox, LawyerLinkIndex index) {
    if (index.clientsByEmail().isEmpty()) {
      return Optional.empty();
    }
    Set<String> addresses =
        EmailAddresses.extractAll(
            message.getFromAddress(), message.getToAddresses(), message.getCcAddresses());
    String ownAddress = EmailAddresses.normalize(mailbox.getEmailAddress());
    for (String address : addresses) {
      if (address.equals(ownAddress)) {
        continue;
      }
      UUID clientId = index.clientsByEmail().get(address);
      if (clientId != null) {
        return Optional.of(new EmailLink(null, clientId, EmailLinkSource.ADDRESS));
      }
    }
    return Optional.empty();
  }
}
