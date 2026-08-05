package com.pravoos.ai.practice.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.pravoos.ai.practice.internal.model.entity.Case;
import com.pravoos.ai.practice.internal.model.entity.Client;
import com.pravoos.ai.practice.internal.model.entity.EmailMessage;
import com.pravoos.ai.practice.internal.model.entity.Mailbox;
import com.pravoos.ai.practice.internal.repository.jpa.CaseRepository;
import com.pravoos.ai.practice.internal.repository.jpa.ClientRepository;
import com.pravoos.ai.practice.internal.repository.jpa.EmailMessageRepository;
import com.pravoos.ai.practice.internal.service.EmailLinkResolver.EmailLink;
import com.pravoos.ai.practice.internal.service.EmailLinkResolver.LawyerLinkIndex;
import com.pravoos.ai.shared.model.enums.EmailDirection;
import com.pravoos.ai.shared.model.enums.EmailLinkSource;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class EmailLinkResolverTest {

  @Mock private CaseRepository caseRepository;
  @Mock private ClientRepository clientRepository;
  @Mock private EmailMessageRepository emailMessageRepository;

  private EmailLinkResolver resolver;

  private final UUID lawyerId = UUID.randomUUID();
  private final UUID mailboxId = UUID.randomUUID();
  private final UUID caseId = UUID.randomUUID();
  private final UUID clientId = UUID.randomUUID();
  private final Mailbox mailbox = new Mailbox();

  @BeforeEach
  void setUp() {
    resolver = new EmailLinkResolver(caseRepository, clientRepository, emailMessageRepository);
    mailbox.setUserId(lawyerId);
    mailbox.setEmailAddress("lawyer@pravoos.ru");
    ReflectionTestUtils.setField(mailbox, "id", mailboxId);
  }

  @Test
  void linksByCaseNumberFoundInSubject() {
    givenCases(caseWithNumber("A40-123456/2024"));
    givenClients();
    EmailMessage message = incoming("opponent@law.ru", "Re: дело № А40-123456/2024, заседание");

    Optional<EmailLink> link = resolver.resolve(message, mailbox, resolver.indexFor(lawyerId));

    assertThat(link).isPresent();
    assertThat(link.get().source()).isEqualTo(EmailLinkSource.CASE_NUMBER);
    assertThat(link.get().caseId()).isEqualTo(caseId);
    assertThat(link.get().clientId()).isEqualTo(clientId);
  }

  @Test
  void inheritsLinkFromAlreadyLinkedMessageInSameThread() {
    givenCases();
    givenClients();
    EmailMessage sibling = incoming("opponent@law.ru", "Первое письмо треда");
    sibling.applyLink(caseId, clientId, EmailLinkSource.MANUAL);
    ReflectionTestUtils.setField(sibling, "id", UUID.randomUUID());
    when(emailMessageRepository.findLinkedInThread(mailboxId, "<thread-1@law.ru>"))
        .thenReturn(List.of(sibling));

    EmailMessage message = incoming("opponent@law.ru", "Re: без номера дела");
    message.setThreadKey("<thread-1@law.ru>");

    Optional<EmailLink> link = resolver.resolve(message, mailbox, resolver.indexFor(lawyerId));

    assertThat(link).isPresent();
    assertThat(link.get().source()).isEqualTo(EmailLinkSource.THREAD);
    assertThat(link.get().caseId()).isEqualTo(caseId);
    assertThat(link.get().clientId()).isEqualTo(clientId);
  }

  @Test
  void linksByClientAddressIgnoringOwnMailboxAddress() {
    givenCases();
    givenClients(clientWithEmail("Client Ltd <Client@Company.ru>"));
    EmailMessage message = new EmailMessage(mailboxId, "<m-2@law.ru>", 2L, EmailDirection.OUT);
    message.setFromAddress("lawyer@pravoos.ru");
    message.setToAddresses("client@company.ru");
    message.setSubject("Договор на согласование");

    Optional<EmailLink> link = resolver.resolve(message, mailbox, resolver.indexFor(lawyerId));

    assertThat(link).isPresent();
    assertThat(link.get().source()).isEqualTo(EmailLinkSource.ADDRESS);
    assertThat(link.get().caseId()).isNull();
    assertThat(link.get().clientId()).isEqualTo(clientId);
  }

  @Test
  void caseNumberWinsOverThreadAndAddress() {
    givenCases(caseWithNumber("А40-1/2026"));
    givenClients(clientWithEmail("client@company.ru"));
    EmailMessage message = incoming("client@company.ru", "Дело А40-1/2026: уточнение");
    message.setThreadKey("<thread-9@law.ru>");

    Optional<EmailLink> link = resolver.resolve(message, mailbox, resolver.indexFor(lawyerId));

    assertThat(link).isPresent();
    assertThat(link.get().source()).isEqualTo(EmailLinkSource.CASE_NUMBER);
  }

  @Test
  void returnsEmptyWhenNothingMatches() {
    givenCases(caseWithNumber("А40-1/2026"));
    givenClients(clientWithEmail("client@company.ru"));
    EmailMessage message = incoming("spam@promo.ru", "Скидки на подписку");

    assertThat(resolver.resolve(message, mailbox, resolver.indexFor(lawyerId))).isEmpty();
  }

  @Test
  void indexSkipsClientsWithoutEmail() {
    givenCases();
    Client client = new Client();
    client.setLawyerId(lawyerId);
    ReflectionTestUtils.setField(client, "id", clientId);
    givenClients(client);

    LawyerLinkIndex index = resolver.indexFor(lawyerId);

    assertThat(index.clientsByEmail()).isEmpty();
  }

  private void givenCases(Case... cases) {
    when(caseRepository.findByLawyerIdAndCourtCaseNumberIsNotNull(lawyerId))
        .thenReturn(List.of(cases));
  }

  private void givenClients(Client... clients) {
    when(clientRepository.findByLawyerIdOrderByCreatedAtDesc(lawyerId))
        .thenReturn(List.of(clients));
  }

  private Case caseWithNumber(String courtCaseNumber) {
    Case caseEntity = new Case();
    caseEntity.setLawyerId(lawyerId);
    caseEntity.setClientId(clientId);
    caseEntity.setCourtCaseNumber(courtCaseNumber);
    ReflectionTestUtils.setField(caseEntity, "id", caseId);
    return caseEntity;
  }

  private Client clientWithEmail(String email) {
    Client client = new Client();
    client.setLawyerId(lawyerId);
    client.setEmail(email);
    ReflectionTestUtils.setField(client, "id", clientId);
    return client;
  }

  private EmailMessage incoming(String fromAddress, String subject) {
    EmailMessage message = new EmailMessage(mailboxId, "<m-1@law.ru>", 1L, EmailDirection.IN);
    message.setFromAddress(fromAddress);
    message.setToAddresses("lawyer@pravoos.ru");
    message.setSubject(subject);
    return message;
  }
}
