package com.pravoos.ai.practice.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.pravoos.ai.shared.model.enums.ContactType;
import com.pravoos.ai.shared.model.enums.EmailDirection;
import com.pravoos.ai.shared.model.enums.EmailLinkSource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class EmailLinkingServiceTest {

  @Mock private EmailMessageRepository emailMessageRepository;
  @Mock private MailboxRepository mailboxRepository;
  @Mock private ClientContactRepository clientContactRepository;
  @Mock private EmailLinkResolver linkResolver;
  @Mock private CaseService caseService;
  @Mock private ClientService clientService;

  private EmailLinkingService service;

  private final UUID lawyerId = UUID.randomUUID();
  private final UUID mailboxId = UUID.randomUUID();
  private final UUID messageId = UUID.randomUUID();
  private final UUID caseId = UUID.randomUUID();
  private final UUID clientId = UUID.randomUUID();
  private final UUID contactId = UUID.randomUUID();
  private final Mailbox mailbox = new Mailbox();

  @BeforeEach
  void setUp() {
    service =
        new EmailLinkingService(
            emailMessageRepository,
            mailboxRepository,
            clientContactRepository,
            linkResolver,
            caseService,
            clientService);
    mailbox.setUserId(lawyerId);
    mailbox.setEmailAddress("lawyer@pravoos.ru");
    ReflectionTestUtils.setField(mailbox, "id", mailboxId);
  }

  @Test
  void autoLinkWritesEmailContactForResolvedMessage() {
    EmailMessage message = message();
    givenMailboxWithPending(message);
    when(linkResolver.resolve(any(), any(), any()))
        .thenReturn(Optional.of(new EmailLink(caseId, clientId, EmailLinkSource.CASE_NUMBER)));
    givenContactIsSaved();

    assertThat(service.autoLinkMailbox(mailboxId)).isEqualTo(1);

    ArgumentCaptor<ClientContact> saved = ArgumentCaptor.forClass(ClientContact.class);
    verify(clientContactRepository).save(saved.capture());
    assertThat(saved.getValue().getClientId()).isEqualTo(clientId);
    assertThat(saved.getValue().getType()).isEqualTo(ContactType.EMAIL);
    assertThat(saved.getValue().getContactDate()).isEqualTo(message.getSentAt().toLocalDate());
    assertThat(saved.getValue().getNotes()).contains("opponent@law.ru", "Иск по договору");
    assertThat(message.getCaseId()).isEqualTo(caseId);
    assertThat(message.getClientId()).isEqualTo(clientId);
    assertThat(message.getLinkSource()).isEqualTo(EmailLinkSource.CASE_NUMBER);
    assertThat(message.getClientContactId()).isEqualTo(contactId);
  }

  @Test
  void autoLinkLeavesUnresolvedMessageUnlinked() {
    EmailMessage message = message();
    givenMailboxWithPending(message);
    when(linkResolver.resolve(any(), any(), any())).thenReturn(Optional.empty());

    assertThat(service.autoLinkMailbox(mailboxId)).isZero();

    verify(clientContactRepository, never()).save(any());
    assertThat(message.isLinked()).isFalse();
  }

  @Test
  void relinkToSameClientDoesNotDuplicateContact() {
    EmailMessage message = message();
    message.applyLink(null, clientId, EmailLinkSource.MANUAL);
    message.attachClientContact(contactId);
    ClientContact existing = contact(clientId);
    when(emailMessageRepository.findByIdAndUserId(messageId, lawyerId))
        .thenReturn(Optional.of(message));
    when(clientContactRepository.findById(contactId)).thenReturn(Optional.of(existing));

    service.link(messageId, new LinkEmailRequest(null, clientId), lawyerId);

    verify(clientContactRepository, never()).save(any());
    verify(clientContactRepository, never()).delete(any());
    assertThat(message.getClientContactId()).isEqualTo(contactId);
  }

  @Test
  void manualLinkTakesClientFromCase() {
    EmailMessage message = message();
    UUID otherClientId = UUID.randomUUID();
    when(emailMessageRepository.findByIdAndUserId(messageId, lawyerId))
        .thenReturn(Optional.of(message));
    when(caseService.requireOwnedCase(caseId, lawyerId)).thenReturn(caseWithClient());
    givenContactIsSaved();

    EmailMessageResponse response =
        service.link(messageId, new LinkEmailRequest(caseId, otherClientId), lawyerId);

    assertThat(response.caseId()).isEqualTo(caseId);
    assertThat(response.clientId()).isEqualTo(clientId);
    assertThat(response.linkSource()).isEqualTo(EmailLinkSource.MANUAL);
    verify(clientService, never()).requireOwnedClient(any(), any());
  }

  @Test
  void manualLinkWithoutTargetIsRejected() {
    assertThatThrownBy(() -> service.link(messageId, new LinkEmailRequest(null, null), lawyerId))
        .isInstanceOf(EmailLinkTargetRequiredException.class);

    verify(emailMessageRepository, never()).findByIdAndUserId(any(), any());
  }

  @Test
  void foreignMessageIsNotFound() {
    when(emailMessageRepository.findByIdAndUserId(messageId, lawyerId))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.unlink(messageId, lawyerId))
        .isInstanceOf(EmailMessageNotFoundException.class);
  }

  @Test
  void unlinkDeletesContactAndClearsLink() {
    EmailMessage message = message();
    message.applyLink(caseId, clientId, EmailLinkSource.CASE_NUMBER);
    message.attachClientContact(contactId);
    ClientContact existing = contact(clientId);
    when(emailMessageRepository.findByIdAndUserId(messageId, lawyerId))
        .thenReturn(Optional.of(message));
    when(clientContactRepository.findById(contactId)).thenReturn(Optional.of(existing));

    EmailMessageResponse response = service.unlink(messageId, lawyerId);

    verify(clientContactRepository).delete(existing);
    assertThat(response.caseId()).isNull();
    assertThat(response.clientId()).isNull();
    assertThat(response.linkSource()).isNull();
    assertThat(message.getClientContactId()).isNull();
  }

  private void givenMailboxWithPending(EmailMessage message) {
    when(mailboxRepository.findById(mailboxId)).thenReturn(Optional.of(mailbox));
    when(emailMessageRepository.findByMailboxIdAndCaseIdIsNullAndClientIdIsNullOrderBySentAtAsc(
            mailboxId))
        .thenReturn(List.of(message));
    when(linkResolver.indexFor(lawyerId)).thenReturn(new LawyerLinkIndex(Map.of(), Map.of()));
  }

  private void givenContactIsSaved() {
    when(clientContactRepository.save(any()))
        .thenAnswer(
            invocation -> {
              ClientContact contact = invocation.getArgument(0);
              ReflectionTestUtils.setField(contact, "id", contactId);
              return contact;
            });
  }

  private EmailMessage message() {
    EmailMessage message = new EmailMessage(mailboxId, "<m-1@law.ru>", 1L, EmailDirection.IN);
    message.setFromAddress("opponent@law.ru");
    message.setToAddresses("lawyer@pravoos.ru");
    message.setSubject("Иск по договору поставки");
    message.setSentAt(LocalDateTime.of(2026, 3, 14, 9, 30));
    ReflectionTestUtils.setField(message, "id", messageId);
    return message;
  }

  private Case caseWithClient() {
    Case caseEntity = new Case();
    caseEntity.setLawyerId(lawyerId);
    caseEntity.setClientId(clientId);
    ReflectionTestUtils.setField(caseEntity, "id", caseId);
    return caseEntity;
  }

  private ClientContact contact(UUID owner) {
    ClientContact contact = new ClientContact();
    contact.setClientId(owner);
    contact.setType(ContactType.EMAIL);
    ReflectionTestUtils.setField(contact, "id", contactId);
    return contact;
  }
}
