package com.pravoos.ai.recyclebin.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pravoos.ai.recyclebin.api.RecycleBinEntityType;
import com.pravoos.ai.recyclebin.internal.config.RecycleBinProperties;
import com.pravoos.ai.recyclebin.internal.model.DeletedEntry;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RecycleBinPurgeServiceTest {

  @Mock private RecycleBinService recycleBinService;

  private RecycleBinPurgeService purgeService(boolean enabled) {
    return new RecycleBinPurgeService(
        recycleBinService,
        new RecycleBinProperties(enabled, Duration.ofDays(7), new RecycleBinProperties.Purge(2)));
  }

  private DeletedEntry expiredEntry() {
    DeletedEntry entry = new DeletedEntry();
    ReflectionTestUtils.setField(entry, "id", UUID.randomUUID());
    entry.setEntityType(RecycleBinEntityType.CASE);
    entry.setEntityId(UUID.randomUUID().toString());
    return entry;
  }

  @Test
  void purgesEveryExpiredEntryInTheBatch() {
    DeletedEntry first = expiredEntry();
    DeletedEntry second = expiredEntry();
    when(recycleBinService.findExpired(PageRequest.of(0, 2))).thenReturn(List.of(first, second));

    assertThat(purgeService(true).purgeExpired()).isEqualTo(2);

    verify(recycleBinService).purge(first);
    verify(recycleBinService).purge(second);
  }

  @Test
  void oneFailingEntryDoesNotStopTheBatch() {
    DeletedEntry failing = expiredEntry();
    DeletedEntry healthy = expiredEntry();
    when(recycleBinService.findExpired(PageRequest.of(0, 2))).thenReturn(List.of(failing, healthy));
    doThrow(new IllegalStateException("storage down")).when(recycleBinService).purge(failing);

    assertThat(purgeService(true).purgeExpired()).isEqualTo(1);

    verify(recycleBinService).purge(healthy);
  }

  @Test
  void doesNothingWhenRecycleBinIsDisabled() {
    assertThat(purgeService(false).purgeExpired()).isZero();

    verify(recycleBinService, never()).findExpired(any());
  }
}
