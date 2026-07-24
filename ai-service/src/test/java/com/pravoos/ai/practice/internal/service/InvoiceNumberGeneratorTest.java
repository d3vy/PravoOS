package com.pravoos.ai.practice.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
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
    when(invoiceRepository.countByLawyerIdAndNumberStartingWith(lawyerId, "СЧ-2026-"))
        .thenReturn(0L);
    InvoiceNumberGenerator generator = new InvoiceNumberGenerator(invoiceRepository);

    assertThat(generator.next(lawyerId, 2026)).isEqualTo("СЧ-2026-0001");
  }

  @Test
  void next_continuesSequenceWithZeroPadding() {
    when(invoiceRepository.countByLawyerIdAndNumberStartingWith(lawyerId, "СЧ-2026-"))
        .thenReturn(41L);
    InvoiceNumberGenerator generator = new InvoiceNumberGenerator(invoiceRepository);

    assertThat(generator.next(lawyerId, 2026)).isEqualTo("СЧ-2026-0042");
  }

  @Test
  void bump_incrementsTrailingSequence() {
    InvoiceNumberGenerator generator = new InvoiceNumberGenerator(invoiceRepository);

    assertThat(generator.bump("СЧ-2026-0009")).isEqualTo("СЧ-2026-0010");
  }
}
