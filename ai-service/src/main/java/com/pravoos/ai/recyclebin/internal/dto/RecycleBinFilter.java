package com.pravoos.ai.recyclebin.internal.dto;

import com.pravoos.ai.recyclebin.api.DeletionRole;
import com.pravoos.ai.recyclebin.api.RecycleBinArea;
import java.time.LocalDateTime;
import java.util.UUID;

public record RecycleBinFilter(
    UUID orgId,
    RecycleBinArea area,
    DeletionRole deletedByRole,
    LocalDateTime from,
    LocalDateTime to,
    String query) {}
