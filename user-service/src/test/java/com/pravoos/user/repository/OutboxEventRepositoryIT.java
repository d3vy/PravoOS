package com.pravoos.user.repository;

import com.pravoos.user.shared.model.entity.OutboxEvent;
import com.pravoos.user.shared.repository.OutboxEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class OutboxEventRepositoryIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Test
    void persistsAndReturnsUnpublishedBatch() {
        outboxEventRepository.save(new OutboxEvent("lawyer.deleted", "key-1", "{\"userId\":\"x\"}"));
        outboxEventRepository.save(new OutboxEvent("application.submitted", "key-2", "{\"id\":\"y\"}"));

        List<OutboxEvent> batch = outboxEventRepository.lockUnpublishedBatch(PageRequest.of(0, 10));

        assertThat(batch).hasSize(2);
        assertThat(batch).extracting(OutboxEvent::getTopic)
                .containsExactlyInAnyOrder("lawyer.deleted", "application.submitted");
        assertThat(batch).allSatisfy(event -> assertThat(event.getPublishedAt()).isNull());
    }

    @Test
    void publishedEventsAreExcludedFromBatch() {
        OutboxEvent event = outboxEventRepository.save(new OutboxEvent("lawyer.deleted", "key-3", "{}"));
        event.markPublished();
        outboxEventRepository.saveAndFlush(event);

        List<OutboxEvent> batch = outboxEventRepository.lockUnpublishedBatch(PageRequest.of(0, 10));

        assertThat(batch).noneSatisfy(e -> assertThat(e.getId()).isEqualTo(event.getId()));
    }
}
