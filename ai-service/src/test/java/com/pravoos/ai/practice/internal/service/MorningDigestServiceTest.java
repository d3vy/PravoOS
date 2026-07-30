package com.pravoos.ai.practice.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pravoos.ai.practice.internal.model.entity.Case;
import com.pravoos.ai.practice.internal.model.entity.Invoice;
import com.pravoos.ai.practice.internal.model.entity.LawyerDigestSent;
import com.pravoos.ai.practice.internal.repository.jpa.CaseRepository;
import com.pravoos.ai.practice.internal.repository.jpa.CaseTaskRepository;
import com.pravoos.ai.practice.internal.repository.jpa.InvoiceRepository;
import com.pravoos.ai.practice.internal.repository.jpa.LawyerDigestSentRepository;
import com.pravoos.ai.shared.client.UserServiceClient;
import com.pravoos.ai.shared.event.LawyerDigestKafkaPayload;
import com.pravoos.ai.shared.model.enums.InvoiceStatus;
import com.pravoos.ai.shared.service.OutboxEventService;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MorningDigestServiceTest {

  @Mock private CaseRepository caseRepository;
  @Mock private CaseTaskRepository caseTaskRepository;
  @Mock private InvoiceRepository invoiceRepository;
  @Mock private LawyerDigestSentRepository digestSentRepository;
  @Mock private UserServiceClient userServiceClient;
  @Mock private OutboxEventService outboxEventService;

  private MorningDigestService service;

  private final UUID lawyerId = UUID.randomUUID();
  private final LocalDate today = LocalDate.of(2026, 7, 30);

  @BeforeEach
  void setUp() {
    service =
        new MorningDigestService(
            caseRepository,
            caseTaskRepository,
            invoiceRepository,
            digestSentRepository,
            userServiceClient,
            outboxEventService,
            null);
  }

  @Test
  void enqueuesDigest_whenLawyerHasUnpaidInvoices() {
    when(digestSentRepository.existsByLawyerIdAndDigestDate(lawyerId, today)).thenReturn(false);
    when(caseTaskRepository.countDueTodayOrOverdueByLawyerId(
            eq(lawyerId), anyCollection(), eq(today)))
        .thenReturn(0L);
    when(caseRepository.findCasesWithUpcomingDeadlines(eq(lawyerId), anyCollection(), any(), any()))
        .thenReturn(List.of());
    when(caseTaskRepository.findUpcomingByLawyerId(eq(lawyerId), anyCollection(), any(), any()))
        .thenReturn(List.of());
    when(invoiceRepository.findByLawyerIdAndStatusOrderByDueDateAsc(lawyerId, InvoiceStatus.ISSUED))
        .thenReturn(List.of(invoice(new BigDecimal("1000.00")), invoice(new BigDecimal("500.50"))));

    boolean result = service.enqueueDigest(lawyerId, today);

    assertThat(result).isTrue();
    ArgumentCaptor<LawyerDigestSent> sentCaptor = ArgumentCaptor.forClass(LawyerDigestSent.class);
    verify(digestSentRepository).save(sentCaptor.capture());
    assertThat(sentCaptor.getValue().getLawyerId()).isEqualTo(lawyerId);
    assertThat(sentCaptor.getValue().getDigestDate()).isEqualTo(today);

    ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
    verify(outboxEventService)
        .enqueue(eq("lawyer.digest.morning"), eq(lawyerId.toString()), payloadCaptor.capture());
    LawyerDigestKafkaPayload payload = (LawyerDigestKafkaPayload) payloadCaptor.getValue();
    assertThat(payload.lawyerId()).isEqualTo(lawyerId);
    assertThat(payload.digestDate()).isEqualTo(today.toString());
    assertThat(payload.tasksTodayCount()).isEqualTo(0);
    assertThat(payload.upcomingDeadlinesCount()).isEqualTo(0);
    assertThat(payload.unpaidInvoicesCount()).isEqualTo(2);
    assertThat(payload.unpaidInvoicesTotalFormatted()).isNotBlank();
  }

  @Test
  void doesNotEnqueueDigest_whenAlreadySentToday() {
    when(digestSentRepository.existsByLawyerIdAndDigestDate(lawyerId, today)).thenReturn(true);

    boolean result = service.enqueueDigest(lawyerId, today);

    assertThat(result).isFalse();
    verify(digestSentRepository, never()).save(any());
    verify(outboxEventService, never()).enqueue(any(), any(), any());
  }

  @Test
  void doesNotEnqueueDigest_whenNothingToReport() {
    when(digestSentRepository.existsByLawyerIdAndDigestDate(lawyerId, today)).thenReturn(false);
    when(caseTaskRepository.countDueTodayOrOverdueByLawyerId(
            eq(lawyerId), anyCollection(), eq(today)))
        .thenReturn(0L);
    when(caseRepository.findCasesWithUpcomingDeadlines(eq(lawyerId), anyCollection(), any(), any()))
        .thenReturn(List.of());
    when(caseTaskRepository.findUpcomingByLawyerId(eq(lawyerId), anyCollection(), any(), any()))
        .thenReturn(List.of());
    when(invoiceRepository.findByLawyerIdAndStatusOrderByDueDateAsc(lawyerId, InvoiceStatus.ISSUED))
        .thenReturn(List.of());

    boolean result = service.enqueueDigest(lawyerId, today);

    assertThat(result).isFalse();
    verify(digestSentRepository, never()).save(any());
    verify(outboxEventService, never()).enqueue(any(), any(), any());
  }

  @Test
  void countsUpcomingDeadlines_withinHorizon() {
    when(digestSentRepository.existsByLawyerIdAndDigestDate(lawyerId, today)).thenReturn(false);
    when(caseTaskRepository.countDueTodayOrOverdueByLawyerId(
            eq(lawyerId), anyCollection(), eq(today)))
        .thenReturn(0L);
    Case caseWithDeadlines = caseEntity();
    caseWithDeadlines.setFilingDeadline(today.plusDays(2));
    caseWithDeadlines.setNextHearingDate(today.plusDays(9));
    when(caseRepository.findCasesWithUpcomingDeadlines(eq(lawyerId), anyCollection(), any(), any()))
        .thenReturn(List.of(caseWithDeadlines));
    when(caseTaskRepository.findUpcomingByLawyerId(eq(lawyerId), anyCollection(), any(), any()))
        .thenReturn(List.of());
    when(invoiceRepository.findByLawyerIdAndStatusOrderByDueDateAsc(lawyerId, InvoiceStatus.ISSUED))
        .thenReturn(List.of());

    service.enqueueDigest(lawyerId, today);

    ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
    verify(outboxEventService).enqueue(any(), any(), payloadCaptor.capture());
    LawyerDigestKafkaPayload payload = (LawyerDigestKafkaPayload) payloadCaptor.getValue();
    assertThat(payload.upcomingDeadlinesCount()).isEqualTo(1);
  }

  private Invoice invoice(BigDecimal total) {
    Invoice invoice = new Invoice();
    setField(invoice, "id", UUID.randomUUID());
    invoice.setLawyerId(lawyerId);
    invoice.setTotal(total);
    return invoice;
  }

  private Case caseEntity() {
    Case caseEntity = new Case();
    setField(caseEntity, "id", UUID.randomUUID());
    caseEntity.setLawyerId(lawyerId);
    return caseEntity;
  }

  private void setField(Object target, String name, Object value) {
    try {
      Field field = target.getClass().getDeclaredField(name);
      field.setAccessible(true);
      field.set(target, value);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }
}
