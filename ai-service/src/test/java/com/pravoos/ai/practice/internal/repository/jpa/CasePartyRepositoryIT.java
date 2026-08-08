package com.pravoos.ai.practice.internal.repository.jpa;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
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
class CasePartyRepositoryIT {

  private static final int MIN_LENGTH = 3;
  private static final int MAX_HITS = 20;

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(
          DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

  @Autowired private CasePartyRepository casePartyRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  private final UUID lawyerId = UUID.randomUUID();

  private UUID caseWithParty(String caseTitle, String partyName) {
    UUID caseId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO cases (id, lawyer_id, title) VALUES (?, ?, ?)", caseId, lawyerId, caseTitle);
    jdbcTemplate.update(
        "INSERT INTO case_parties (id, case_id, name, role) VALUES (?, ?, ?, 'Ответчик')",
        UUID.randomUUID(),
        caseId,
        partyName);
    return caseId;
  }

  private List<String> namesFor(String normalizedQuery) {
    return casePartyRepository
        .searchConflicts(lawyerId, normalizedQuery, MIN_LENGTH, MAX_HITS)
        .stream()
        .map(CasePartyRepository.PartyLookup::getPartyName)
        .toList();
  }

  @Test
  void matchesCaseInsensitivelyAndCollapsesWhitespaceOnBothSides() {
    caseWithParty("Спор о поставке", "  Иван   ИВАНОВ ");

    assertThat(namesFor("иван иванов")).containsExactly("  Иван   ИВАНОВ ");
  }

  @Test
  void matchesInBothContainmentDirections() {
    caseWithParty("Дело А", "ООО \"Ромашка\"");
    caseWithParty("Дело Б", "Иванов");

    assertThat(namesFor("ромашка")).containsExactly("ООО \"Ромашка\"");
    assertThat(namesFor("иванов иван иванович")).containsExactly("Иванов");
  }

  @Test
  void skipsPartiesWhoseNormalizedNameIsShorterThanTheMinimumLength() {
    caseWithParty("Дело", "ИП");

    assertThat(namesFor("ип иванов")).isEmpty();
  }

  @Test
  void doesNotLeakPartiesOfOtherLawyers() {
    UUID otherLawyer = UUID.randomUUID();
    UUID otherCase = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO cases (id, lawyer_id, title) VALUES (?, ?, 'Чужое дело')",
        otherCase,
        otherLawyer);
    jdbcTemplate.update(
        "INSERT INTO case_parties (id, case_id, name, role) VALUES (?, ?, 'ООО Ромашка', 'Истец')",
        UUID.randomUUID(),
        otherCase);

    assertThat(namesFor("ромашка")).isEmpty();
  }

  @Test
  void treatsTheQueryAsALiteralRatherThanAPattern() {
    caseWithParty("Дело", "ООО Ромашка");

    assertThat(namesFor("%ром%")).isEmpty();
  }

  @Test
  void appliesTheHitLimitInSql() {
    for (int index = 0; index < 5; index++) {
      caseWithParty("Дело " + index, "ООО Ромашка " + index);
    }

    assertThat(casePartyRepository.searchConflicts(lawyerId, "ромашка", MIN_LENGTH, 2)).hasSize(2);
  }
}
