package com.pravoos.ai.practice.internal.service;

import com.pravoos.ai.practice.internal.repository.jpa.InvoiceRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class InvoiceNumberGenerator {

  private static final String PREFIX = "СЧ";

  private final InvoiceRepository invoiceRepository;

  public InvoiceNumberGenerator(InvoiceRepository invoiceRepository) {
    this.invoiceRepository = invoiceRepository;
  }

  public String next(UUID lawyerId, int year) {
    String yearPrefix = PREFIX + "-" + year + "-";
    long used = invoiceRepository.countByLawyerIdAndNumberStartingWith(lawyerId, yearPrefix);
    return format(yearPrefix, used + 1);
  }

  public String bump(String number) {
    int lastDash = number.lastIndexOf('-');
    String yearPrefix = number.substring(0, lastDash + 1);
    long sequence = Long.parseLong(number.substring(lastDash + 1)) + 1;
    return format(yearPrefix, sequence);
  }

  private String format(String yearPrefix, long sequence) {
    return yearPrefix + String.format("%04d", sequence);
  }
}
