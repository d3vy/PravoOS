package com.pravoos.ai.recyclebin.internal.repository.jpa;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers(disabledWithoutDocker = true)
class SoftDeleteIndexDirectionIT {

  private static final String SEED_SCRIPT = "db/soft-delete-index-seed.sql";

  private static final List<String> SOFT_DELETE_TABLES =
      List.of(
          "cases",
          "clients",
          "documents",
          "invoices",
          "document_templates",
          "saved_views",
          "workflow_definitions");

  private static final List<String> ACTIVE_ROW_INDEXES =
      List.of(
          "idx_cases_lawyer_active",
          "idx_cases_lawyer_status_active",
          "idx_cases_client_active",
          "idx_cases_org_status_active",
          "idx_clients_lawyer_active",
          "idx_documents_case_active",
          "idx_documents_uploaded_by_active",
          "idx_invoices_lawyer_active",
          "idx_invoices_client_active",
          "idx_document_templates_lawyer_active",
          "idx_saved_views_owner_active",
          "idx_saved_views_team_active",
          "idx_workflow_definitions_created_by_active",
          "idx_workflow_definitions_org_active",
          "idx_workflow_definitions_system_active");

  private static boolean seeded;

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(
          DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void seedRealisticVolumeOnce() {
    if (seeded) {
      return;
    }
    jdbcTemplate.execute(readSeedScript());
    seeded = true;
  }

  @Test
  void noSoftDeleteTableKeepsAnIndexAimedAtDeletedRows() {
    List<String> wrongDirection =
        jdbcTemplate.queryForList(
            "SELECT indexname FROM pg_indexes "
                + "WHERE tablename = ANY (string_to_array(?, ',')) "
                + "AND indexdef ILIKE '%deleted_at IS NOT NULL%'",
            String.class, String.join(",", SOFT_DELETE_TABLES));

    assertThat(wrongDirection).isEmpty();
  }

  @Test
  void everyActiveRowIndexExistsAndIsPartialOnLiveRows() {
    List<String> existing =
        jdbcTemplate.queryForList(
            "SELECT indexname FROM pg_indexes WHERE indexdef ILIKE '%deleted_at IS NULL%'",
            String.class);

    assertThat(existing).containsAll(ACTIVE_ROW_INDEXES);
  }

  @Test
  void concurrentIndexBuildLeftNothingInvalid() {
    List<String> invalid =
        jdbcTemplate.queryForList(
            "SELECT c.relname FROM pg_index i JOIN pg_class c ON c.oid = i.indexrelid "
                + "WHERE NOT i.indisvalid OR NOT i.indisready",
            String.class);

    assertThat(invalid).isEmpty();
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "idx_cases_lawyer_active | SELECT id FROM cases WHERE lawyer_id = :owner"
            + " AND deleted_at IS NULL ORDER BY created_at DESC LIMIT 20",
        "idx_cases_lawyer_status_active | SELECT id FROM cases WHERE lawyer_id = :owner"
            + " AND status = 'INTAKE' AND deleted_at IS NULL ORDER BY created_at DESC LIMIT 20",
        "idx_cases_client_active | SELECT id FROM cases WHERE client_id = :client"
            + " AND deleted_at IS NULL ORDER BY created_at DESC LIMIT 20",
        "idx_cases_org_status_active | SELECT id FROM cases WHERE org_id = :org"
            + " AND status = 'INTAKE' AND deleted_at IS NULL",
        "idx_clients_lawyer_active | SELECT id FROM clients WHERE lawyer_id = :owner"
            + " AND deleted_at IS NULL ORDER BY created_at DESC LIMIT 20",
        "idx_documents_case_active | SELECT id FROM documents WHERE case_id = :kase"
            + " AND deleted_at IS NULL ORDER BY uploaded_at DESC LIMIT 20",
        "idx_documents_uploaded_by_active | SELECT id FROM documents WHERE uploaded_by = :owner"
            + " AND document_kind = 'GENERAL' AND deleted_at IS NULL",
        "idx_invoices_lawyer_active | SELECT id FROM invoices WHERE lawyer_id = :owner"
            + " AND deleted_at IS NULL ORDER BY created_at DESC LIMIT 20",
        "idx_invoices_client_active | SELECT id FROM invoices WHERE client_id = :client"
            + " AND deleted_at IS NULL ORDER BY created_at DESC LIMIT 20",
        "idx_document_templates_lawyer_active | SELECT id FROM document_templates"
            + " WHERE lawyer_id = :owner AND deleted_at IS NULL ORDER BY created_at DESC LIMIT 20",
        "idx_workflow_definitions_system_active | SELECT id FROM workflow_definitions"
            + " WHERE is_system AND deleted_at IS NULL ORDER BY category, name"
      })
  void hotReadPathIsServedByThePartialIndex(String expectedIndex, String query) {
    String plan = planFor(query);

    assertThat(plan).contains(expectedIndex);
    assertThat(plan).doesNotContain("Seq Scan");
  }

  @Test
  void savedViewCatalogCombinesOwnerAndTeamPartials() {
    String plan =
        planFor(
            "SELECT id FROM saved_views WHERE scope = 'CASES'"
                + " AND (lawyer_id = :owner OR (shared_with_team AND org_id IN (:org)))"
                + " AND deleted_at IS NULL ORDER BY created_at");

    assertThat(plan).contains("idx_saved_views_owner_active");
    assertThat(plan).contains("idx_saved_views_team_active");
    assertThat(plan).doesNotContain("Seq Scan");
  }

  @Test
  void workflowCatalogCombinesSystemOwnerAndOrgPartials() {
    String plan =
        planFor(
            "SELECT id FROM workflow_definitions"
                + " WHERE (is_system OR created_by = :owner OR org_id IN (:org))"
                + " AND deleted_at IS NULL ORDER BY is_system DESC, category, name");

    assertThat(plan).contains("idx_workflow_definitions_system_active");
    assertThat(plan).contains("idx_workflow_definitions_created_by_active");
    assertThat(plan).contains("idx_workflow_definitions_org_active");
    assertThat(plan).doesNotContain("Seq Scan");
  }

  private String planFor(String query) {
    String bound =
        query
            .replace(":owner", "'00000000-0000-0000-0000-000000000007'")
            .replace(":client", "'00000000-0000-0001-0000-000000000007'")
            .replace(":kase", "'00000000-0000-0003-0000-000000000007'")
            .replace(":org", "'00000000-0000-0002-0000-000000000007'");

    return String.join("\n", jdbcTemplate.queryForList("EXPLAIN " + bound, String.class));
  }

  private static String readSeedScript() {
    try {
      return new ClassPathResource(SEED_SCRIPT).getContentAsString(StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException("Не прочитан seed-скрипт " + SEED_SCRIPT, e);
    }
  }
}
