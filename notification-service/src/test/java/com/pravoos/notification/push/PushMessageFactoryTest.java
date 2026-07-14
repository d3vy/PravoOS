package com.pravoos.notification.push;

import com.pravoos.notification.event.CaseDeadlineKafkaPayload;
import com.pravoos.notification.event.CaseHearingUpdatedKafkaPayload;
import com.pravoos.notification.event.CaseMessageCreatedKafkaPayload;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PushMessageFactoryTest {

    private final PushMessageFactory factory = new PushMessageFactory();

    @Test
    void deadline_pointsToLawyerCase() {
        UUID caseId = UUID.randomUUID();
        CaseDeadlineKafkaPayload payload = new CaseDeadlineKafkaPayload(
                caseId, UUID.randomUUID(), "Иванов против ООО", "Подача апелляции", "2026-08-01", 3);

        PushMessage message = factory.deadline(payload);

        assertThat(message.title()).contains("Иванов против ООО");
        assertThat(message.body()).contains("Подача апелляции").contains("3");
        assertThat(message.url()).isEqualTo("/cases/" + caseId);
        assertThat(message.tag()).isEqualTo("deadline-" + caseId);
    }

    @Test
    void hearing_describesDateChange() {
        UUID caseId = UUID.randomUUID();
        CaseHearingUpdatedKafkaPayload payload = new CaseHearingUpdatedKafkaPayload(
                caseId, UUID.randomUUID(), "Иванов против ООО", "А40-1/2026", "2026-08-01", "2026-09-01");

        PushMessage message = factory.hearing(payload);

        assertThat(message.body()).contains("2026-08-01").contains("2026-09-01");
        assertThat(message.url()).isEqualTo("/cases/" + caseId);
    }

    @Test
    void caseMessage_usesPortalLink_forClientRecipient() {
        UUID caseId = UUID.randomUUID();
        CaseMessageCreatedKafkaPayload payload = caseMessage(caseId, "LAWYER");

        PushMessage message = factory.caseMessage(payload, false);

        assertThat(message.url()).isEqualTo("/portal/cases/" + caseId);
        assertThat(message.body()).startsWith("Ваш юрист:");
    }

    @Test
    void caseMessage_usesLawyerLink_forLawyerRecipient() {
        UUID caseId = UUID.randomUUID();
        CaseMessageCreatedKafkaPayload payload = caseMessage(caseId, "CLIENT");

        PushMessage message = factory.caseMessage(payload, true);

        assertThat(message.url()).isEqualTo("/cases/" + caseId);
        assertThat(message.body()).startsWith("Клиент:");
    }

    @Test
    void caseMessage_fallsBackToGenericPreview_whenPreviewIsBlank() {
        CaseMessageCreatedKafkaPayload payload = new CaseMessageCreatedKafkaPayload(
                UUID.randomUUID(), "Дело", UUID.randomUUID(), "CLIENT",
                UUID.randomUUID(), UUID.randomUUID(), null, "  ");

        PushMessage message = factory.caseMessage(payload, true);

        assertThat(message.body()).isEqualTo("Клиент: Новое сообщение");
    }

    private CaseMessageCreatedKafkaPayload caseMessage(UUID caseId, String authorRole) {
        return new CaseMessageCreatedKafkaPayload(caseId, "Иванов против ООО", UUID.randomUUID(), authorRole,
                UUID.randomUUID(), UUID.randomUUID(), null, "Добрый день");
    }
}
