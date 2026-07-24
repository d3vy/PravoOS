package com.pravoos.ai.practice.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.pravoos.ai.practice.internal.dto.CaseMessageResponse;
import com.pravoos.ai.practice.internal.model.entity.Case;
import com.pravoos.ai.practice.internal.model.entity.CaseMessage;
import com.pravoos.ai.practice.internal.repository.jpa.CaseMessageRepository;
import com.pravoos.ai.practice.internal.repository.jpa.CaseThreadReadRepository;
import com.pravoos.ai.shared.event.CaseMessageCreatedKafkaPayload;
import com.pravoos.ai.shared.model.enums.MessageAuthorRole;
import com.pravoos.ai.shared.service.OutboxEventService;
import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CaseMessageServiceTest {

  @Mock private CaseMessageRepository caseMessageRepository;
  @Mock private CaseThreadReadRepository caseThreadReadRepository;
  @Mock private CaseService caseService;
  @Mock private PortalCaseService portalCaseService;
  @Mock private OutboxEventService outboxEventService;

  private CaseMessageService service;

  private final UUID caseId = UUID.randomUUID();
  private final UUID lawyerId = UUID.randomUUID();
  private final UUID clientId = UUID.randomUUID();
  private final UUID clientUserId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    service =
        new CaseMessageService(
            caseMessageRepository,
            caseThreadReadRepository,
            caseService,
            portalCaseService,
            outboxEventService);
    lenient()
        .when(caseMessageRepository.save(any(CaseMessage.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
  }

  private Case caseEntity(UUID withClientId) {
    Case caseEntity = new Case();
    caseEntity.setLawyerId(lawyerId);
    caseEntity.setClientId(withClientId);
    caseEntity.setTitle("Дело");
    setId(caseEntity, caseId);
    return caseEntity;
  }

  private void setId(Case caseEntity, UUID id) {
    try {
      Field field = Case.class.getDeclaredField("id");
      field.setAccessible(true);
      field.set(caseEntity, id);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }

  @Test
  void postLawyerMessage_savesAsLawyer_andNotifiesClient() {
    when(caseService.requireVisibleCase(caseId, lawyerId, List.of()))
        .thenReturn(caseEntity(clientId));

    CaseMessageResponse response =
        service.postLawyerMessage(caseId, "Здравствуйте", lawyerId, List.of());

    assertThat(response.authorRole()).isEqualTo(MessageAuthorRole.LAWYER);
    CaseMessageCreatedKafkaPayload payload = capturePayload();
    assertThat(payload.recipientClientId()).isEqualTo(clientId);
    assertThat(payload.recipientLawyerId()).isNull();
    assertThat(payload.authorRole()).isEqualTo("LAWYER");
  }

  @Test
  void postLawyerMessage_noClientOnCase_doesNotNotify() {
    when(caseService.requireVisibleCase(caseId, lawyerId, List.of())).thenReturn(caseEntity(null));

    service.postLawyerMessage(caseId, "Заметка", lawyerId, List.of());

    verify(outboxEventService, never()).enqueue(any(), any(), any());
  }

  @Test
  void postClientMessage_savesAsClient_andNotifiesLawyer() {
    when(portalCaseService.requireClientCase(caseId, List.of(clientId)))
        .thenReturn(caseEntity(clientId));

    CaseMessageResponse response =
        service.postClientMessage(caseId, "Вопрос", clientUserId, List.of(clientId));

    assertThat(response.authorRole()).isEqualTo(MessageAuthorRole.CLIENT);
    assertThat(response.authorUserId()).isEqualTo(clientUserId);
    CaseMessageCreatedKafkaPayload payload = capturePayload();
    assertThat(payload.recipientLawyerId()).isEqualTo(lawyerId);
    assertThat(payload.recipientClientId()).isNull();
  }

  @Test
  void findClientThread_delegatesScopeCheck() {
    when(portalCaseService.requireClientCase(caseId, List.of(clientId)))
        .thenReturn(caseEntity(clientId));
    when(caseMessageRepository.findByCaseIdOrderByCreatedAtAsc(caseId)).thenReturn(List.of());

    assertThat(service.findClientThread(caseId, List.of(clientId))).isEmpty();
    verify(portalCaseService).requireClientCase(caseId, List.of(clientId));
  }

  private CaseMessageCreatedKafkaPayload capturePayload() {
    ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
    verify(outboxEventService).enqueue(eq("case.message.created"), any(), captor.capture());
    return (CaseMessageCreatedKafkaPayload) captor.getValue();
  }
}
