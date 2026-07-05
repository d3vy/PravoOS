package com.pravoos.user.service;

import com.pravoos.user.shared.service.OutboxEventService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pravoos.user.registration.internal.event.LawyerDeletedKafkaPayload;
import com.pravoos.user.shared.model.entity.OutboxEvent;
import com.pravoos.user.shared.repository.OutboxEventRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OutboxEventServiceTest {

    private final OutboxEventRepository repository = mock(OutboxEventRepository.class);
    private final OutboxEventService service = new OutboxEventService(repository, new ObjectMapper());

    @Test
    void enqueue_persistsEvent_withSerializedPayload() {
        UUID userId = UUID.randomUUID();

        service.enqueue("lawyer.deleted", userId.toString(), new LawyerDeletedKafkaPayload(userId, Map.of()));

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(repository).save(captor.capture());
        OutboxEvent saved = captor.getValue();

        assertThat(saved.getTopic()).isEqualTo("lawyer.deleted");
        assertThat(saved.getKafkaKey()).isEqualTo(userId.toString());
        assertThat(saved.getPayload()).contains(userId.toString());
        assertThat(saved.getPublishedAt()).isNull();
        assertThat(saved.getAttempts()).isZero();
    }
}
