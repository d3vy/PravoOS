package com.pravoos.ai.practice.internal.repository.jpa;

import com.pravoos.ai.practice.internal.model.entity.CaseTask;
import com.pravoos.ai.shared.model.enums.CaseStatus;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CaseTaskRepository extends JpaRepository<CaseTask, UUID> {

  interface UpcomingTaskView {
    UUID getCaseId();

    String getCaseTitle();

    LocalDate getDueDate();
  }

  interface TodayTaskView {
    UUID getId();

    UUID getCaseId();

    String getCaseTitle();

    String getText();

    LocalDate getDueDate();
  }

  interface LawyerCountView {
    UUID getLawyerId();

    long getCount();
  }

  interface LawyerUpcomingTaskView {
    UUID getLawyerId();

    LocalDate getDueDate();
  }

  interface TaskReminderView {
    UUID getId();

    UUID getCaseId();

    UUID getLawyerId();

    String getCaseTitle();

    String getText();

    LocalDate getDueDate();
  }

  List<CaseTask> findByCaseIdOrderByDoneAscCreatedAtAsc(UUID caseId);

  List<CaseTask> findByCaseIdInAndDoneFalseAndDueDateBetween(
      Collection<UUID> caseIds, LocalDate from, LocalDate to);

  @Query(
      "SELECT COUNT(t) FROM CaseTask t WHERE t.done = false "
          + "AND t.caseId IN (SELECT c.id FROM Case c WHERE c.lawyerId = :lawyerId)")
  long countOpenByLawyerId(@Param("lawyerId") UUID lawyerId);

  @Query(
      """
            SELECT c.id AS caseId, c.title AS caseTitle, t.dueDate AS dueDate
            FROM CaseTask t, Case c
            WHERE t.caseId = c.id
              AND t.done = false
              AND c.lawyerId = :lawyerId
              AND c.status NOT IN :closedStatuses
              AND t.dueDate BETWEEN :today AND :horizon
            """)
  List<UpcomingTaskView> findUpcomingByLawyerId(
      @Param("lawyerId") UUID lawyerId,
      @Param("closedStatuses") Collection<CaseStatus> closedStatuses,
      @Param("today") LocalDate today,
      @Param("horizon") LocalDate horizon);

  @Query(
      """
            SELECT t.id AS id, c.id AS caseId, c.title AS caseTitle, t.text AS text, t.dueDate AS dueDate
            FROM CaseTask t, Case c
            WHERE t.caseId = c.id
              AND t.done = false
              AND c.lawyerId = :lawyerId
              AND c.status NOT IN :closedStatuses
              AND t.dueDate <= :today
            ORDER BY t.dueDate ASC
            """)
  List<TodayTaskView> findDueTodayOrOverdueByLawyerId(
      @Param("lawyerId") UUID lawyerId,
      @Param("closedStatuses") Collection<CaseStatus> closedStatuses,
      @Param("today") LocalDate today);

  @Modifying
  @Query(
      "DELETE FROM CaseTask t WHERE t.caseId IN (SELECT c.id FROM Case c WHERE c.lawyerId = :lawyerId)")
  int deleteByLawyerId(@Param("lawyerId") UUID lawyerId);

  @Query(
      value =
          """
            SELECT t.id AS id, c.id AS caseId, c.lawyerId AS lawyerId, c.title AS caseTitle,
                   t.text AS text, t.dueDate AS dueDate
            FROM CaseTask t, Case c
            WHERE t.caseId = c.id
              AND t.done = false
              AND t.dueDate = :target
              AND c.status NOT IN :excludedStatuses
            """,
      countQuery =
          """
            SELECT COUNT(t) FROM CaseTask t, Case c
            WHERE t.caseId = c.id
              AND t.done = false
              AND t.dueDate = :target
              AND c.status NOT IN :excludedStatuses
            """)
  Page<TaskReminderView> findDueOnDate(
      @Param("target") LocalDate target,
      @Param("excludedStatuses") Collection<CaseStatus> excludedStatuses,
      Pageable pageable);

  @Query(
      """
            SELECT DISTINCT c.lawyerId FROM CaseTask t, Case c
            WHERE t.caseId = c.id
              AND t.done = false
              AND c.status NOT IN :closedStatuses
              AND t.dueDate <= :today
            """)
  Page<UUID> findDistinctLawyerIdsWithTasksDueTodayOrOverdue(
      @Param("closedStatuses") Collection<CaseStatus> closedStatuses,
      @Param("today") LocalDate today,
      Pageable pageable);

  @Query(
      "SELECT COUNT(t) FROM CaseTask t, Case c WHERE t.caseId = c.id "
          + "AND t.done = false AND c.lawyerId = :lawyerId "
          + "AND c.status NOT IN :closedStatuses AND t.dueDate <= :today")
  long countDueTodayOrOverdueByLawyerId(
      @Param("lawyerId") UUID lawyerId,
      @Param("closedStatuses") Collection<CaseStatus> closedStatuses,
      @Param("today") LocalDate today);

  @Query(
      """
            SELECT c.lawyerId AS lawyerId, COUNT(t) AS count FROM CaseTask t, Case c
            WHERE t.caseId = c.id
              AND t.done = false
              AND c.lawyerId IN :lawyerIds
              AND c.status NOT IN :closedStatuses
              AND t.dueDate <= :today
            GROUP BY c.lawyerId
            """)
  List<LawyerCountView> countDueTodayOrOverdueByLawyerIdIn(
      @Param("lawyerIds") Collection<UUID> lawyerIds,
      @Param("closedStatuses") Collection<CaseStatus> closedStatuses,
      @Param("today") LocalDate today);

  @Query(
      """
            SELECT c.lawyerId AS lawyerId, t.dueDate AS dueDate
            FROM CaseTask t, Case c
            WHERE t.caseId = c.id
              AND t.done = false
              AND c.lawyerId IN :lawyerIds
              AND c.status NOT IN :closedStatuses
              AND t.dueDate BETWEEN :today AND :horizon
            """)
  List<LawyerUpcomingTaskView> findUpcomingByLawyerIdIn(
      @Param("lawyerIds") Collection<UUID> lawyerIds,
      @Param("closedStatuses") Collection<CaseStatus> closedStatuses,
      @Param("today") LocalDate today,
      @Param("horizon") LocalDate horizon);
}
