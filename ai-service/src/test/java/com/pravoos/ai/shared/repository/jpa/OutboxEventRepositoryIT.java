package com.pravoos.ai.shared.repository.jpa;

import static org.assertj.core.api.Assertions.assertThat;

import com.pravoos.ai.shared.model.entity.OutboxEvent;
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
class OutboxEventRepositoryIT {

  private static final int MAX_ATTEMPTS = 10;

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(
          DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

  @Autowired private OutboxEventRepository outboxEventRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  private void event(String key, int attempts, boolean published) {
    jdbcTemplate.update(
        "INSERT INTO outbox_events (topic, kafka_key, payload, attempts, published_at)"
            + " VALUES ('case.updated', ?, '{}', ?, ?)",
        key,
        attempts,
        published ? java.sql.Timestamp.valueOf(java.time.LocalDateTime.now()) : null);
  }

  @Test
  void parkedEventsNoLongerBlockTheQueue() {
    event("poison", MAX_ATTEMPTS, false);
    event("retriable", MAX_ATTEMPTS - 1, false);
    event("fresh", 0, false);
    event("done", 0, true);

    assertThat(outboxEventRepository.lockUnpublishedBatch(MAX_ATTEMPTS, PageRequest.of(0, 50)))
        .extracting(OutboxEvent::getKafkaKey)
        .containsExactlyInAnyOrder("retriable", "fresh");
  }

  @Test
  void batchSizeCapsHowManyEventsAreLockedAtOnce() {
    event("a", 0, false);
    event("b", 0, false);
    event("c", 0, false);

    assertThat(outboxEventRepository.lockUnpublishedBatch(MAX_ATTEMPTS, PageRequest.of(0, 2)))
        .hasSize(2);
  }
}
