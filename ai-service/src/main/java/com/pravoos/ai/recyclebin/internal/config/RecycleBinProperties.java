package com.pravoos.ai.recyclebin.internal.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "recycle-bin")
public record RecycleBinProperties(boolean enabled, Duration retention, Purge purge) {

  private static final Duration DEFAULT_RETENTION = Duration.ofDays(7);
  private static final int DEFAULT_BATCH_SIZE = 100;

  public RecycleBinProperties {
    retention = retention == null ? DEFAULT_RETENTION : retention;
    purge = purge == null ? new Purge(DEFAULT_BATCH_SIZE) : purge;
  }

  public record Purge(int batchSize) {
    public Purge {
      batchSize = batchSize <= 0 ? DEFAULT_BATCH_SIZE : batchSize;
    }
  }
}
