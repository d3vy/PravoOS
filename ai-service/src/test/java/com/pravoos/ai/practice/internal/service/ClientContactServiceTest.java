package com.pravoos.ai.practice.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pravoos.ai.practice.internal.dto.ContactResponse;
import com.pravoos.ai.practice.internal.dto.CreateContactRequest;
import com.pravoos.ai.practice.internal.dto.UpdateContactRequest;
import com.pravoos.ai.practice.internal.model.entity.ClientContact;
import com.pravoos.ai.practice.internal.repository.jpa.ClientContactRepository;
import com.pravoos.ai.shared.exception.ClientContactNotFoundException;
import com.pravoos.ai.shared.model.enums.ContactType;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ClientContactServiceTest {

  @Mock private ClientContactRepository contactRepository;
  @Mock private ClientService clientService;

  @InjectMocks private ClientContactService clientContactService;

  private ClientContact contactWithId(UUID id, UUID clientId) {
    ClientContact contact = new ClientContact();
    ReflectionTestUtils.setField(contact, "id", id);
    contact.setClientId(clientId);
    contact.setType(ContactType.CALL);
    contact.setContactDate(LocalDate.now());
    return contact;
  }

  @Test
  void findByClientChecksOwnershipAndMapsPage() {
    UUID clientId = UUID.randomUUID();
    UUID lawyerId = UUID.randomUUID();
    ClientContact contact = contactWithId(UUID.randomUUID(), clientId);
    when(contactRepository.findByClientIdOrderByContactDateDescCreatedAtDesc(any(), any()))
        .thenReturn(new PageImpl<>(java.util.List.of(contact)));

    Page<ContactResponse> result = clientContactService.findByClient(clientId, lawyerId, 0, 20);

    verify(clientService).requireOwnedClient(clientId, lawyerId);
    assertThat(result.getContent()).hasSize(1);
    assertThat(result.getContent().get(0).clientId()).isEqualTo(clientId);
  }

  @Test
  void createChecksOwnershipAndSavesContact() {
    UUID clientId = UUID.randomUUID();
    UUID lawyerId = UUID.randomUUID();
    CreateContactRequest request =
        new CreateContactRequest(ContactType.MEETING, LocalDate.now(), "  ");

    when(contactRepository.save(any(ClientContact.class)))
        .thenAnswer(
            invocation -> {
              ClientContact saved = invocation.getArgument(0);
              ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());
              return saved;
            });

    ContactResponse response = clientContactService.create(clientId, request, lawyerId);

    verify(clientService).requireOwnedClient(clientId, lawyerId);
    ArgumentCaptor<ClientContact> captor = ArgumentCaptor.forClass(ClientContact.class);
    verify(contactRepository).save(captor.capture());
    assertThat(captor.getValue().getClientId()).isEqualTo(clientId);
    assertThat(captor.getValue().getNotes()).isNull();
    assertThat(response.type()).isEqualTo(ContactType.MEETING);
  }

  @Test
  void updateModifiesContactBelongingToClient() {
    UUID clientId = UUID.randomUUID();
    UUID lawyerId = UUID.randomUUID();
    UUID contactId = UUID.randomUUID();
    ClientContact contact = contactWithId(contactId, clientId);
    when(contactRepository.findById(contactId)).thenReturn(Optional.of(contact));

    UpdateContactRequest request =
        new UpdateContactRequest(ContactType.LETTER, LocalDate.now().minusDays(1), "note");

    ContactResponse response = clientContactService.update(clientId, contactId, request, lawyerId);

    verify(clientService).requireOwnedClient(clientId, lawyerId);
    assertThat(response.type()).isEqualTo(ContactType.LETTER);
    assertThat(response.notes()).isEqualTo("note");
  }

  @Test
  void updateThrowsWhenContactBelongsToDifferentClient() {
    UUID clientId = UUID.randomUUID();
    UUID otherClientId = UUID.randomUUID();
    UUID lawyerId = UUID.randomUUID();
    UUID contactId = UUID.randomUUID();
    ClientContact contact = contactWithId(contactId, otherClientId);
    when(contactRepository.findById(contactId)).thenReturn(Optional.of(contact));

    UpdateContactRequest request =
        new UpdateContactRequest(ContactType.CALL, LocalDate.now(), null);

    assertThatThrownBy(() -> clientContactService.update(clientId, contactId, request, lawyerId))
        .isInstanceOf(ClientContactNotFoundException.class);
  }

  @Test
  void updateThrowsWhenContactNotFound() {
    UUID clientId = UUID.randomUUID();
    UUID lawyerId = UUID.randomUUID();
    UUID contactId = UUID.randomUUID();
    when(contactRepository.findById(contactId)).thenReturn(Optional.empty());

    UpdateContactRequest request =
        new UpdateContactRequest(ContactType.CALL, LocalDate.now(), null);

    assertThatThrownBy(() -> clientContactService.update(clientId, contactId, request, lawyerId))
        .isInstanceOf(ClientContactNotFoundException.class);
  }

  @Test
  void deleteRemovesContactBelongingToClient() {
    UUID clientId = UUID.randomUUID();
    UUID lawyerId = UUID.randomUUID();
    UUID contactId = UUID.randomUUID();
    ClientContact contact = contactWithId(contactId, clientId);
    when(contactRepository.findById(contactId)).thenReturn(Optional.of(contact));

    clientContactService.delete(clientId, contactId, lawyerId);

    verify(clientService).requireOwnedClient(clientId, lawyerId);
    verify(contactRepository).delete(contact);
  }

  @Test
  void deleteThrowsWhenContactBelongsToDifferentClientAndDoesNotDelete() {
    UUID clientId = UUID.randomUUID();
    UUID otherClientId = UUID.randomUUID();
    UUID lawyerId = UUID.randomUUID();
    UUID contactId = UUID.randomUUID();
    ClientContact contact = contactWithId(contactId, otherClientId);
    when(contactRepository.findById(contactId)).thenReturn(Optional.of(contact));

    assertThatThrownBy(() -> clientContactService.delete(clientId, contactId, lawyerId))
        .isInstanceOf(ClientContactNotFoundException.class);
    verify(contactRepository, never()).delete(any());
  }
}
