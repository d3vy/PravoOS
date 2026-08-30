package com.pravoos.ai.recyclebin.internal.repository.jpa;

import static org.assertj.core.api.Assertions.assertThat;

import com.pravoos.ai.document.internal.repository.jpa.DocumentRepository;
import com.pravoos.ai.practice.internal.model.SavedViewScope;
import com.pravoos.ai.practice.internal.model.entity.WorkflowDefinition;
import com.pravoos.ai.practice.internal.repository.jpa.CaseRepository;
import com.pravoos.ai.practice.internal.repository.jpa.ClientRepository;
import com.pravoos.ai.practice.internal.repository.jpa.DocumentTemplateRepository;
import com.pravoos.ai.practice.internal.repository.jpa.MailboxRepository;
import com.pravoos.ai.practice.internal.repository.jpa.SavedViewRepository;
import com.pravoos.ai.practice.internal.repository.jpa.WorkflowDefinitionRepository;
import com.pravoos.ai.shared.config.PiiCryptoConfig;
import com.pravoos.common.security.PiiCryptoProperties;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@Import(PiiCryptoConfig.class)
@EnableConfigurationProperties(PiiCryptoProperties.class)
class SoftDeleteVisibilityIT {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(
          DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

  @Autowired private CaseRepository caseRepository;
  @Autowired private ClientRepository clientRepository;
  @Autowired private DocumentRepository documentRepository;
  @Autowired private DocumentTemplateRepository documentTemplateRepository;
  @Autowired private SavedViewRepository savedViewRepository;
  @Autowired private WorkflowDefinitionRepository workflowDefinitionRepository;
  @Autowired private MailboxRepository mailboxRepository;
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

  private UUID insertDocumentTemplate(String name) {
    UUID templateId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO document_templates (id, lawyer_id, name, content, created_at) "
            + "VALUES (?, ?, ?, 'content', now())",
        templateId,
        lawyerId,
        name);
    return templateId;
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

  private UUID insertSavedView(String name) {
    UUID viewId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO saved_views (id, lawyer_id, scope, name, config, shared_with_team, "
            + "created_at, updated_at) "
            + "VALUES (?, ?, 'CASES', ?, '{}', FALSE, now(), now())",
        viewId,
        lawyerId,
        name);
    return viewId;
  }

  private UUID insertWorkflowDefinition(String name) {
    UUID definitionId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO workflow_definitions (id, created_by, name, category, is_system, steps, "
            + "created_at, updated_at) "
            + "VALUES (?, ?, ?, 'CUSTOM', FALSE, '[]'::jsonb, now(), now())",
        definitionId,
        lawyerId,
        name);
    return definitionId;
  }

  private UUID insertMailbox(String emailAddress) {
    UUID mailboxId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO mailboxes (id, user_id, email_address, imap_host, imap_port, imap_ssl, "
            + "password_enc, folder, sync_enabled, status, created_at, updated_at) "
            + "VALUES (?, ?, ?, 'imap.example.com', 993, TRUE, 'enc', 'INBOX', TRUE, 'PENDING', "
            + "now(), now())",
        mailboxId,
        lawyerId,
        emailAddress);
    return mailboxId;
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

  @Test
  void softDeletedTemplateDisappearsFromEveryReadAndComesBackOnRestore() {
    UUID templateId = insertDocumentTemplate("Иск в суд");

    assertThat(documentTemplateRepository.findById(templateId)).isPresent();
    assertThat(documentTemplateRepository.findByLawyerIdOrderByCreatedAtDesc(lawyerId)).hasSize(1);

    documentTemplateRepository.softDelete(templateId, LocalDateTime.now(ZoneOffset.UTC));

    assertThat(documentTemplateRepository.findById(templateId)).isEmpty();
    assertThat(documentTemplateRepository.findByLawyerIdOrderByCreatedAtDesc(lawyerId)).isEmpty();
    assertThat(documentTemplateRepository.findByIdAndLawyerId(templateId, lawyerId)).isEmpty();

    documentTemplateRepository.restore(templateId);

    assertThat(documentTemplateRepository.findById(templateId)).isPresent();
    assertThat(documentTemplateRepository.findByLawyerIdOrderByCreatedAtDesc(lawyerId)).hasSize(1);
  }

  @Test
  void purgedTemplateIsPhysicallyRemoved() {
    UUID templateId = insertDocumentTemplate("Черновик, который снесут");
    documentTemplateRepository.softDelete(templateId, LocalDateTime.now(ZoneOffset.UTC));

    documentTemplateRepository.hardDelete(templateId);

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM document_templates WHERE id = ?", Integer.class, templateId))
        .isZero();
  }

  @Test
  void softDeletedSavedViewDisappearsFromEveryReadAndComesBackOnRestore() {
    UUID viewId = insertSavedView("Мои дела");

    assertThat(savedViewRepository.findById(viewId)).isPresent();
    assertThat(savedViewRepository.findByIdAndLawyerId(viewId, lawyerId)).isPresent();
    assertThat(
            savedViewRepository.findVisible(
                lawyerId, List.of(UUID.randomUUID()), SavedViewScope.CASES))
        .hasSize(1);

    savedViewRepository.softDelete(viewId, LocalDateTime.now(ZoneOffset.UTC));

    assertThat(savedViewRepository.findById(viewId)).isEmpty();
    assertThat(savedViewRepository.findByIdAndLawyerId(viewId, lawyerId)).isEmpty();
    assertThat(
            savedViewRepository.findVisible(
                lawyerId, List.of(UUID.randomUUID()), SavedViewScope.CASES))
        .isEmpty();

    savedViewRepository.restore(viewId);

    assertThat(savedViewRepository.findById(viewId)).isPresent();
  }

  @Test
  void purgedSavedViewIsPhysicallyRemoved() {
    UUID viewId = insertSavedView("Черновик, который снесут");
    savedViewRepository.softDelete(viewId, LocalDateTime.now(ZoneOffset.UTC));

    savedViewRepository.hardDelete(viewId);

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM saved_views WHERE id = ?", Integer.class, viewId))
        .isZero();
  }

  @Test
  void softDeletedWorkflowDefinitionDisappearsFromEveryReadAndComesBackOnRestore() {
    UUID definitionId = insertWorkflowDefinition("Процесс взыскания");

    assertThat(workflowDefinitionRepository.findById(definitionId)).isPresent();
    assertThat(workflowDefinitionRepository.findVisible(lawyerId, List.of(UUID.randomUUID())))
        .extracting(WorkflowDefinition::getId)
        .contains(definitionId);

    workflowDefinitionRepository.softDelete(definitionId, LocalDateTime.now(ZoneOffset.UTC));

    assertThat(workflowDefinitionRepository.findById(definitionId)).isEmpty();
    assertThat(workflowDefinitionRepository.findVisible(lawyerId, List.of(UUID.randomUUID())))
        .extracting(WorkflowDefinition::getId)
        .doesNotContain(definitionId);

    workflowDefinitionRepository.restore(definitionId);

    assertThat(workflowDefinitionRepository.findById(definitionId)).isPresent();
    assertThat(workflowDefinitionRepository.findVisible(lawyerId, List.of(UUID.randomUUID())))
        .extracting(WorkflowDefinition::getId)
        .contains(definitionId);
  }

  @Test
  void purgedWorkflowDefinitionIsPhysicallyRemoved() {
    UUID definitionId = insertWorkflowDefinition("Черновик, который снесут");
    workflowDefinitionRepository.softDelete(definitionId, LocalDateTime.now(ZoneOffset.UTC));

    workflowDefinitionRepository.hardDelete(definitionId);

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM workflow_definitions WHERE id = ?",
                Integer.class,
                definitionId))
        .isZero();
  }

  @Test
  void softDeletedMailboxDisappearsFromEveryReadAndComesBackOnRestore() {
    UUID mailboxId = insertMailbox("lawyer@example.com");

    assertThat(mailboxRepository.findById(mailboxId)).isPresent();
    assertThat(mailboxRepository.findByUserIdOrderByCreatedAtAsc(lawyerId))
        .extracting(mailbox -> mailbox.getId())
        .contains(mailboxId);

    mailboxRepository.softDelete(mailboxId, LocalDateTime.now(ZoneOffset.UTC));

    assertThat(mailboxRepository.findById(mailboxId)).isEmpty();
    assertThat(mailboxRepository.findByIdAndUserId(mailboxId, lawyerId)).isEmpty();
    assertThat(mailboxRepository.findByUserIdOrderByCreatedAtAsc(lawyerId))
        .extracting(mailbox -> mailbox.getId())
        .doesNotContain(mailboxId);

    mailboxRepository.restore(mailboxId);

    assertThat(mailboxRepository.findById(mailboxId)).isPresent();
    assertThat(mailboxRepository.findByUserIdOrderByCreatedAtAsc(lawyerId))
        .extracting(mailbox -> mailbox.getId())
        .contains(mailboxId);
  }

  @Test
  void purgedMailboxIsPhysicallyRemoved() {
    UUID mailboxId = insertMailbox("gone@example.com");
    mailboxRepository.softDelete(mailboxId, LocalDateTime.now(ZoneOffset.UTC));

    mailboxRepository.hardDelete(mailboxId);

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mailboxes WHERE id = ?", Integer.class, mailboxId))
        .isZero();
  }
}
