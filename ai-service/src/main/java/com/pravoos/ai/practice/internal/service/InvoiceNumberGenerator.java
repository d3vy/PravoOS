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
    long lastUsed =
        invoiceRepository.findMaxNumberSequence(
            lawyerId, "^" + yearPrefix + "[0-9]+$", yearPrefix.length() + 1);
    return format(yearPrefix, lastUsed + 1);
  }

  private String format(String yearPrefix, long sequence) {
    return yearPrefix + String.format("%04d", sequence);
  }
}
