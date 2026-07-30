package com.pravoos.notification.push;

import static org.assertj.core.api.Assertions.assertThat;

import com.pravoos.notification.event.CaseDeadlineKafkaPayload;
import com.pravoos.notification.event.CaseHearingUpdatedKafkaPayload;
import com.pravoos.notification.event.CaseMessageCreatedKafkaPayload;
import com.pravoos.notification.event.InvoiceOverdueKafkaPayload;
import com.pravoos.notification.event.LawyerDigestKafkaPayload;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PushMessageFactoryTest {

  private final PushMessageFactory factory = new PushMessageFactory();

  @Test
  void deadline_pointsToLawyerCase() {
    UUID caseId = UUID.randomUUID();
    CaseDeadlineKafkaPayload payload =
        new CaseDeadlineKafkaPayload(
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
    CaseHearingUpdatedKafkaPayload payload =
        new CaseHearingUpdatedKafkaPayload(
            caseId,
            UUID.randomUUID(),
            "Иванов против ООО",
            "А40-1/2026",
            "2026-08-01",
            "2026-09-01");

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
    CaseMessageCreatedKafkaPayload payload =
        new CaseMessageCreatedKafkaPayload(
            UUID.randomUUID(),
            "Дело",
            UUID.randomUUID(),
            "CLIENT",
            UUID.randomUUID(),
            UUID.randomUUID(),
            null,
            "  ");

    PushMessage message = factory.caseMessage(payload, true);

    assertThat(message.body()).isEqualTo("Клиент: Новое сообщение");
  }

  @Test
  void invoiceOverdue_pointsToInvoiceAndMentionsClientAndAmount() {
    UUID invoiceId = UUID.randomUUID();
    InvoiceOverdueKafkaPayload payload =
        new InvoiceOverdueKafkaPayload(
            invoiceId, UUID.randomUUID(), "СЧ-2026-0007", "ООО Ромашка", "2 500.00 RUB", 3);

    PushMessage message = factory.invoiceOverdue(payload);

    assertThat(message.title()).contains("СЧ-2026-0007");
    assertThat(message.body()).contains("ООО Ромашка").contains("2 500.00 RUB").contains("3");
    assertThat(message.url()).isEqualTo("/invoices/" + invoiceId);
    assertThat(message.tag()).isEqualTo("invoice-overdue-" + invoiceId + "-3");
  }

  @Test
  void morningDigest_listsOnlyNonZeroSections() {
    UUID lawyerId = UUID.randomUUID();
    LawyerDigestKafkaPayload payload =
        new LawyerDigestKafkaPayload(lawyerId, "2026-07-30", 2, 0, 1, "1 500,00");

    PushMessage message = factory.morningDigest(payload);

    assertThat(message.title()).isEqualTo("Утренний дайджест");
    assertThat(message.body()).contains("2 задачи на сегодня").contains("1 500,00");
    assertThat(message.body()).doesNotContain("дедлайн");
    assertThat(message.url()).isEqualTo("/dashboard");
    assertThat(message.tag()).isEqualTo("digest-" + lawyerId + "-2026-07-30");
  }

  @Test
  void morningDigest_usesSingularForOneItem() {
    LawyerDigestKafkaPayload payload =
        new LawyerDigestKafkaPayload(UUID.randomUUID(), "2026-07-30", 1, 1, 1, "500,00");

    PushMessage message = factory.morningDigest(payload);

    assertThat(message.body())
        .contains("1 задача на сегодня")
        .contains("1 дедлайн на неделе")
        .contains("1 неоплаченный счёт на 500,00");
  }

  private CaseMessageCreatedKafkaPayload caseMessage(UUID caseId, String authorRole) {
    return new CaseMessageCreatedKafkaPayload(
        caseId,
        "Иванов против ООО",
        UUID.randomUUID(),
        authorRole,
        UUID.randomUUID(),
        UUID.randomUUID(),
        null,
        "Добрый день");
  }
}
