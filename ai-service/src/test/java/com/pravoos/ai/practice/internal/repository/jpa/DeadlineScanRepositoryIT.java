package com.pravoos.ai.practice.internal.repository.jpa;

import static org.assertj.core.api.Assertions.assertThat;

import com.pravoos.ai.practice.internal.model.entity.Case;
import com.pravoos.ai.shared.model.enums.CaseStatus;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class DeadlineScanRepositoryIT {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(
          DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

  @Autowired private CaseRepository caseRepository;
  @Autowired private CaseTaskRepository caseTaskRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  private final UUID lawyerId = UUID.randomUUID();
  private final LocalDate target = LocalDate.of(2026, 9, 1);

  private UUID caseWithDeadlines(String title, CaseStatus status) {
    UUID caseId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO cases (id, lawyer_id, title, status, filing_deadline, next_hearing_date,"
            + " expires_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
        caseId,
        lawyerId,
        title,
        status.name(),
        target,
        target,
        target);
    return caseId;
  }

  private void openTaskDueOnTarget(UUID caseId) {
    jdbcTemplate.update(
        "INSERT INTO case_tasks (id, case_id, text, due_date, done) VALUES (?, ?, ?, ?, false)",
        UUID.randomUUID(),
        caseId,
        "Подготовить возражения",
        target);
  }

  @Test
  void deadlineScansSkipClosedCases() {
    UUID openCase = caseWithDeadlines("Открытое дело", CaseStatus.IN_PROGRESS);
    caseWithDeadlines("Выигранное дело", CaseStatus.CLOSED_WON);
    caseWithDeadlines("Проигранное дело", CaseStatus.CLOSED_LOST);

    assertThat(caseRepository.findByFilingDeadlineAndStatusNotIn(target, CaseStatus.CLOSED))
        .extracting(Case::getId)
        .containsExactly(openCase);
    assertThat(caseRepository.findByNextHearingDateAndStatusNotIn(target, CaseStatus.CLOSED))
        .extracting(Case::getId)
        .containsExactly(openCase);
    assertThat(caseRepository.findByExpiresAtAndStatusNotIn(target, CaseStatus.CLOSED))
        .extracting(Case::getId)
        .containsExactly(openCase);
  }

  @Test
  void taskScanSkipsTasksThatBelongToClosedCases() {
    UUID openCase = caseWithDeadlines("Открытое дело", CaseStatus.SUBMITTED);
    UUID closedCase = caseWithDeadlines("Закрытое дело", CaseStatus.CLOSED_WON);
    openTaskDueOnTarget(openCase);
    openTaskDueOnTarget(closedCase);

    assertThat(caseTaskRepository.findDueOnDate(target, CaseStatus.CLOSED))
        .extracting(CaseTaskRepository.TaskReminderView::getCaseId)
        .containsExactly(openCase);
  }
}
