package com.pravoos.ai.recyclebin.internal.service;

import com.pravoos.ai.recyclebin.internal.config.RecycleBinProperties;
import com.pravoos.ai.recyclebin.internal.model.DeletedEntry;
import java.util.List;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class RecycleBinPurgeService {

  private static final Logger log = LoggerFactory.getLogger(RecycleBinPurgeService.class);

  private final RecycleBinService recycleBinService;
  private final RecycleBinProperties properties;

  public RecycleBinPurgeService(
      RecycleBinService recycleBinService, RecycleBinProperties properties) {
    this.recycleBinService = recycleBinService;
    this.properties = properties;
  }

  @Scheduled(cron = "${recycle-bin.purge.cron:0 30 3 * * *}", zone = "UTC")
  @SchedulerLock(
      name = "RecycleBinPurgeService_purgeExpired",
      lockAtLeastFor = "PT1M",
      lockAtMostFor = "PT30M")
  public int purgeExpired() {
    if (!properties.enabled()) {
      return 0;
    }
    List<DeletedEntry> expired =
        recycleBinService.findExpired(PageRequest.of(0, properties.purge().batchSize()));
    int purged = 0;
    for (DeletedEntry entry : expired) {
      try {
        recycleBinService.purge(entry);
        purged++;
      } catch (RuntimeException e) {
        log.error(
            "Failed to purge recycle bin entry {} ({} {}): {}",
            entry.getId(),
            entry.getEntityType(),
            entry.getEntityId(),
            e.getMessage(),
            e);
      }
    }
    if (purged > 0) {
      log.info("Recycle bin purge removed {} of {} expired entries", purged, expired.size());
    }
    return purged;
  }
}
