package com.pravoos.ai.practice.internal.repository.jpa;

import com.pravoos.ai.practice.internal.model.entity.TimeEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TimeEntryRepository extends JpaRepository<TimeEntry, UUID> {

    List<TimeEntry> findByCaseIdOrderByActivityDateDescCreatedAtDesc(UUID caseId);

    Optional<TimeEntry> findByLawyerIdAndRunningTrue(UUID lawyerId);

    List<TimeEntry> findByClientIdAndBillableTrueAndInvoiceIdIsNullAndRunningFalseOrderByActivityDateAsc(UUID clientId);

    @Query("SELECT t FROM TimeEntry t WHERE t.clientId = :clientId AND t.caseId = :caseId "
            + "AND t.billable = true AND t.invoiceId IS NULL AND t.running = false "
            + "ORDER BY t.activityDate ASC")
    List<TimeEntry> findBillableForClientAndCase(@Param("clientId") UUID clientId, @Param("caseId") UUID caseId);

    @Modifying
    @Query("UPDATE TimeEntry t SET t.invoiceId = null WHERE t.invoiceId = :invoiceId")
    void releaseByInvoiceId(@Param("invoiceId") UUID invoiceId);

    @Modifying
    @Query("DELETE FROM TimeEntry t WHERE t.lawyerId = :lawyerId")
    int deleteByLawyerId(@Param("lawyerId") UUID lawyerId);
}
