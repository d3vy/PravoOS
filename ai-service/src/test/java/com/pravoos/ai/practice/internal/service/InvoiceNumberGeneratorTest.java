package com.pravoos.ai.practice.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.pravoos.ai.practice.internal.repository.jpa.InvoiceRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InvoiceNumberGeneratorTest {

  @Mock private InvoiceRepository invoiceRepository;

  private final UUID lawyerId = UUID.randomUUID();

  @Test
  void next_startsAtOneForEmptyYear() {
    when(invoiceRepository.findMaxNumberSequence(lawyerId, "^СЧ-2026-[0-9]+$", 9)).thenReturn(0L);
    InvoiceNumberGenerator generator = new InvoiceNumberGenerator(invoiceRepository);

    assertThat(generator.next(lawyerId, 2026)).isEqualTo("СЧ-2026-0001");
  }

  @Test
  void next_continuesSequenceWithZeroPadding() {
    when(invoiceRepository.findMaxNumberSequence(lawyerId, "^СЧ-2026-[0-9]+$", 9)).thenReturn(41L);
    InvoiceNumberGenerator generator = new InvoiceNumberGenerator(invoiceRepository);

    assertThat(generator.next(lawyerId, 2026)).isEqualTo("СЧ-2026-0042");
  }

  @Test
  void next_skipsNumbersFreedByDeletedDrafts() {
    when(invoiceRepository.findMaxNumberSequence(lawyerId, "^СЧ-2026-[0-9]+$", 9)).thenReturn(7L);
    InvoiceNumberGenerator generator = new InvoiceNumberGenerator(invoiceRepository);

    assertThat(generator.next(lawyerId, 2026)).isEqualTo("СЧ-2026-0008");
  }

  @Test
  void next_neverReusesANumberBeyondFourDigits() {
    when(invoiceRepository.findMaxNumberSequence(lawyerId, "^СЧ-2026-[0-9]+$", 9))
        .thenReturn(9999L);
    InvoiceNumberGenerator generator = new InvoiceNumberGenerator(invoiceRepository);

    assertThat(generator.next(lawyerId, 2026)).isEqualTo("СЧ-2026-10000");
  }

  @Test
  void next_usesTheYearOfTheRequestedPeriod() {
    when(invoiceRepository.findMaxNumberSequence(lawyerId, "^СЧ-2027-[0-9]+$", 9)).thenReturn(0L);
    InvoiceNumberGenerator generator = new InvoiceNumberGenerator(invoiceRepository);

    assertThat(generator.next(lawyerId, 2027)).isEqualTo("СЧ-2027-0001");
  }

  @Test
  void next_readsTheSequenceStartRightAfterThePrefix() {
    when(invoiceRepository.findMaxNumberSequence(
            org.mockito.ArgumentMatchers.eq(lawyerId), anyString(), anyInt()))
        .thenReturn(3L);
    InvoiceNumberGenerator generator = new InvoiceNumberGenerator(invoiceRepository);

    assertThat(generator.next(lawyerId, 2026)).isEqualTo("СЧ-2026-0004");
  }
}
