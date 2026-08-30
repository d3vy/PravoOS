package com.pravoos.ai.practice.internal.repository.jpa;

import com.pravoos.ai.practice.internal.model.entity.InvoiceOverdueReminder;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvoiceOverdueReminderRepository
    extends JpaRepository<InvoiceOverdueReminder, UUID> {

  boolean existsByInvoiceIdAndThresholdDays(UUID invoiceId, int thresholdDays);

  List<UUID> findInvoiceIdByThresholdDaysAndInvoiceIdIn(int thresholdDays, List<UUID> invoiceIds);
}
