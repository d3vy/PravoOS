package com.pravoos.ai.recyclebin.api;

import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeafSoftDeleteRepository<T> extends JpaRepository<T, UUID> {

  int softDelete(UUID id, LocalDateTime deletedAt);

  int restore(UUID id);

  int hardDelete(UUID id);
}
