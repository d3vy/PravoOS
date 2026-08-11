package com.pravoos.ai.practice.internal.repository.jpa;

import com.pravoos.ai.practice.internal.model.entity.TimeEntry;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TimeEntryRepository extends JpaRepository<TimeEntry, UUID> {

  List<TimeEntry> findByCaseIdOrderByActivityDateDescCreatedAtDesc(UUID caseId);

  Optional<TimeEntry> findByLawyerIdAndRunningTrue(UUID lawyerId);

  List<TimeEntry> findByLawyerIdAndBillableTrueAndInvoiceIdIsNullAndRunningFalse(UUID lawyerId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "SELECT t FROM TimeEntry t WHERE t.clientId = :clientId AND t.lawyerId = :lawyerId "
          + "AND t.billable = true AND t.invoiceId IS NULL AND t.running = false "
          + "ORDER BY t.activityDate ASC")
  List<TimeEntry> lockBillableForClient(
      @Param("clientId") UUID clientId, @Param("lawyerId") UUID lawyerId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "SELECT t FROM TimeEntry t WHERE t.clientId = :clientId AND t.caseId = :caseId "
          + "AND t.lawyerId = :lawyerId "
          + "AND t.billable = true AND t.invoiceId IS NULL AND t.running = false "
          + "ORDER BY t.activityDate ASC")
  List<TimeEntry> lockBillableForClientAndCase(
      @Param("clientId") UUID clientId,
      @Param("caseId") UUID caseId,
      @Param("lawyerId") UUID lawyerId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "SELECT t FROM TimeEntry t WHERE t.id IN :ids AND t.clientId = :clientId "
          + "AND t.lawyerId = :lawyerId "
          + "AND t.billable = true AND t.invoiceId IS NULL AND t.running = false "
          + "ORDER BY t.activityDate ASC")
  List<TimeEntry> lockBillableByIds(
      @Param("ids") Collection<UUID> ids,
      @Param("clientId") UUID clientId,
      @Param("lawyerId") UUID lawyerId);

  @Modifying
  @Query("UPDATE TimeEntry t SET t.invoiceId = null WHERE t.invoiceId = :invoiceId")
  void releaseByInvoiceId(@Param("invoiceId") UUID invoiceId);

  @Modifying
  @Query("DELETE FROM TimeEntry t WHERE t.lawyerId = :lawyerId")
  int deleteByLawyerId(@Param("lawyerId") UUID lawyerId);
}
