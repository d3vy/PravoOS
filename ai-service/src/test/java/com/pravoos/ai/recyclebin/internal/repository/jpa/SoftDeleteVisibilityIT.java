package com.pravoos.ai.recyclebin.internal.repository.jpa;

import static org.assertj.core.api.Assertions.assertThat;

import com.pravoos.ai.document.internal.repository.jpa.DocumentRepository;
import com.pravoos.ai.practice.internal.repository.jpa.CaseRepository;
import com.pravoos.ai.practice.internal.repository.jpa.ClientRepository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class SoftDeleteVisibilityIT {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(
          DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

  @Autowired private CaseRepository caseRepository;
  @Autowired private ClientRepository clientRepository;
  @Autowired private DocumentRepository documentRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  private final UUID lawyerId = UUID.randomUUID();

  private UUID insertCase(String title) {
    UUID caseId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO cases (id, lawyer_id, title, status, created_at) "
            + "VALUES (?, ?, ?, 'INTAKE', now())",
        caseId,
        lawyerId,
        title);
    return caseId;
  }

  private UUID insertDocument(UUID caseId, String title) {
    UUID documentId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO documents (id, title, file_name, file_type, file_path, uploaded_by, case_id, "
            + "size_bytes, visible_to_client, document_kind, superseded, uploaded_at, status, "
            + "summary_status) "
            + "VALUES (?, ?, 'f.pdf', 'pdf', '/tmp/f.pdf', ?, ?, 10, FALSE, 'GENERAL', FALSE, "
            + "now(), 'READY', 'NONE')",
        documentId,
        title,
        lawyerId,
        caseId);
    return documentId;
  }

  private UUID insertClient(String name) {
    UUID clientId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO clients (id, lawyer_id, name, type) VALUES (?, ?, ?, 'INDIVIDUAL')",
        clientId,
        lawyerId,
        name);
    return clientId;
  }

  @Test
  void softDeletedCaseDisappearsFromEveryReadAndComesBackOnRestore() {
    UUID caseId = insertCase("Иванов против ООО Ромашка");

    assertThat(caseRepository.findById(caseId)).isPresent();

    caseRepository.softDelete(caseId, LocalDateTime.now(ZoneOffset.UTC));

    assertThat(caseRepository.findById(caseId)).isEmpty();
    assertThat(caseRepository.findByLawyerIdOrderByCreatedAtDesc(lawyerId)).isEmpty();
    assertThat(
            caseRepository
                .findVisible(
                    lawyerId,
                    List.of(UUID.randomUUID()),
                    null,
                    null,
                    null,
                    List.of(UUID.randomUUID()),
                    PageRequest.of(0, 20))
                .getContent())
        .isEmpty();

    caseRepository.restore(caseId);

    assertThat(caseRepository.findById(caseId)).isPresent();
    assertThat(caseRepository.findByLawyerIdOrderByCreatedAtDesc(lawyerId)).hasSize(1);
  }

  @Test
  void softDeletedDocumentIsHiddenFromCaseListingsAndRestorable() {
    UUID caseId = insertCase("Дело с документом");
    UUID documentId = insertDocument(caseId, "Исковое заявление");

    assertThat(documentRepository.findByCaseIdOrderByUploadedAtDesc(caseId)).hasSize(1);

    documentRepository.softDelete(documentId, LocalDateTime.now(ZoneOffset.UTC));

    assertThat(documentRepository.findByCaseIdOrderByUploadedAtDesc(caseId)).isEmpty();
    assertThat(documentRepository.countByCaseId(caseId)).isZero();
    assertThat(documentRepository.findIdsByCaseIdIncludingDeleted(caseId))
        .containsExactly(documentId);

    documentRepository.restore(documentId);

    assertThat(documentRepository.findByCaseIdOrderByUploadedAtDesc(caseId)).hasSize(1);
  }

  @Test
  void softDeletedClientIsHiddenButStillPurgedByTheGdprSweep() {
    UUID clientId = insertClient("Иванов И.И.");
    clientRepository.softDelete(clientId, LocalDateTime.now(ZoneOffset.UTC));

    assertThat(clientRepository.findByLawyerIdOrderByCreatedAtDesc(lawyerId)).isEmpty();

    assertThat(clientRepository.deleteByLawyerId(lawyerId)).isEqualTo(1);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM clients WHERE id = ?", Integer.class, clientId))
        .isZero();
  }
}
