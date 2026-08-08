package com.pravoos.ai.practice.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pravoos.ai.practice.internal.dto.ConflictHit;
import com.pravoos.ai.practice.internal.model.entity.Client;
import com.pravoos.ai.practice.internal.repository.jpa.CasePartyRepository;
import com.pravoos.ai.practice.internal.repository.jpa.ClientRepository;
import com.pravoos.ai.shared.model.enums.ConflictSource;
import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConflictCheckServiceTest {

  @Mock private CasePartyRepository casePartyRepository;
  @Mock private ClientRepository clientRepository;

  private ConflictCheckService service() {
    return new ConflictCheckService(casePartyRepository, clientRepository);
  }

  private CasePartyRepository.PartyLookup party(
      UUID caseId, String caseTitle, String partyName, String role) {
    return new CasePartyRepository.PartyLookup() {
      @Override
      public UUID getCaseId() {
        return caseId;
      }

      @Override
      public String getCaseTitle() {
        return caseTitle;
      }

      @Override
      public String getPartyName() {
        return partyName;
      }

      @Override
      public String getPartyRole() {
        return role;
      }
    };
  }

  @Test
  void matchesExistingCasePartyByNameSubstring() {
    UUID lawyerId = UUID.randomUUID();
    UUID caseId = UUID.randomUUID();
    when(casePartyRepository.searchConflicts(eq(lawyerId), anyString(), anyInt(), anyInt()))
        .thenReturn(List.of(party(caseId, "Спор о поставке", "ООО \"Ромашка\"", "Ответчик")));
    when(clientRepository.findByLawyerIdOrderByCreatedAtDesc(lawyerId)).thenReturn(List.of());

    List<ConflictHit> hits = service().check(lawyerId, "Ромашка", null);

    assertThat(hits).hasSize(1);
    ConflictHit hit = hits.get(0);
    assertThat(hit.source()).isEqualTo(ConflictSource.CASE_PARTY);
    assertThat(hit.caseId()).isEqualTo(caseId);
    assertThat(hit.caseTitle()).isEqualTo("Спор о поставке");
    assertThat(hit.role()).isEqualTo("Ответчик");
  }

  @Test
  void normalizesTheQueryBeforeHandingItToTheRepository() {
    UUID lawyerId = UUID.randomUUID();
    ArgumentCaptor<String> query = ArgumentCaptor.forClass(String.class);
    when(casePartyRepository.searchConflicts(eq(lawyerId), anyString(), anyInt(), anyInt()))
        .thenReturn(List.of());
    when(clientRepository.findByLawyerIdOrderByCreatedAtDesc(lawyerId)).thenReturn(List.of());

    service().check(lawyerId, "  Иван   ИВАНОВ ", null);

    verify(casePartyRepository).searchConflicts(eq(lawyerId), query.capture(), anyInt(), anyInt());
    assertThat(query.getValue()).isEqualTo("иван иванов");
  }

  @Test
  void matchesExistingClientByName() {
    UUID lawyerId = UUID.randomUUID();
    Client client = new Client();
    client.setLawyerId(lawyerId);
    client.setName("Петров Пётр");
    when(casePartyRepository.searchConflicts(eq(lawyerId), anyString(), anyInt(), anyInt()))
        .thenReturn(List.of());
    when(clientRepository.findByLawyerIdOrderByCreatedAtDesc(lawyerId)).thenReturn(List.of(client));

    List<ConflictHit> hits = service().check(lawyerId, "Петров Пётр", null);

    assertThat(hits).hasSize(1);
    assertThat(hits.get(0).source()).isEqualTo(ConflictSource.CLIENT);
    assertThat(hits.get(0).clientId()).isEqualTo(client.getId());
  }

  @Test
  void matchesExistingClientByInn() {
    UUID lawyerId = UUID.randomUUID();
    Client client = new Client();
    client.setLawyerId(lawyerId);
    client.setName("ООО Вектор");
    client.setInn("7701234567");
    when(casePartyRepository.searchConflicts(eq(lawyerId), anyString(), anyInt(), anyInt()))
        .thenReturn(List.of());
    when(clientRepository.findByLawyerIdOrderByCreatedAtDesc(lawyerId)).thenReturn(List.of(client));

    List<ConflictHit> hits = service().check(lawyerId, "7701234567", null);

    assertThat(hits).hasSize(1);
    assertThat(hits.get(0).source()).isEqualTo(ConflictSource.CLIENT);
  }

  @Test
  void excludesGivenClientIdFromResults() throws Exception {
    UUID lawyerId = UUID.randomUUID();
    UUID clientId = UUID.randomUUID();
    Client client = new Client();
    client.setLawyerId(lawyerId);
    client.setName("Сидоров Сидор");
    Field idField = Client.class.getDeclaredField("id");
    idField.setAccessible(true);
    idField.set(client, clientId);
    when(casePartyRepository.searchConflicts(eq(lawyerId), anyString(), anyInt(), anyInt()))
        .thenReturn(List.of());
    when(clientRepository.findByLawyerIdOrderByCreatedAtDesc(lawyerId)).thenReturn(List.of(client));

    List<ConflictHit> hits = service().check(lawyerId, "Сидоров Сидор", clientId);

    assertThat(hits).isEmpty();
  }

  @Test
  void returnsNoHitsWhenNothingMatches() {
    UUID lawyerId = UUID.randomUUID();
    Client client = new Client();
    client.setLawyerId(lawyerId);
    client.setName("ООО Альфа");
    when(casePartyRepository.searchConflicts(eq(lawyerId), anyString(), anyInt(), anyInt()))
        .thenReturn(List.of());
    when(clientRepository.findByLawyerIdOrderByCreatedAtDesc(lawyerId)).thenReturn(List.of(client));

    List<ConflictHit> hits = service().check(lawyerId, "Совершенно другое имя", null);

    assertThat(hits).isEmpty();
  }

  @Test
  void ignoresQueriesShorterThanMinLength() {
    UUID lawyerId = UUID.randomUUID();

    List<ConflictHit> hits = service().check(lawyerId, "ИП", null);

    assertThat(hits).isEmpty();
  }
}
