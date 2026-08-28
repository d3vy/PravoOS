package com.pravoos.ai.recyclebin.internal.controller;

import com.pravoos.ai.recyclebin.api.DeletionActor;
import com.pravoos.ai.recyclebin.api.DeletionRole;
import com.pravoos.ai.recyclebin.api.RecycleBinArea;
import com.pravoos.ai.recyclebin.internal.dto.RecycleBinEntryResponse;
import com.pravoos.ai.recyclebin.internal.dto.RecycleBinFilter;
import com.pravoos.ai.recyclebin.internal.model.DeletedEntry;
import com.pravoos.ai.recyclebin.internal.service.RecycleBinService;
import com.pravoos.ai.shared.util.PageRequests;
import com.pravoos.ai.shared.util.PagedResponse;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai/recycle-bin")
public class RecycleBinController {

  private final RecycleBinService recycleBinService;

  public RecycleBinController(RecycleBinService recycleBinService) {
    this.recycleBinService = recycleBinService;
  }

  @GetMapping
  public ResponseEntity<List<RecycleBinEntryResponse>> list(
      @RequestParam(required = false) RecycleBinArea area,
      @RequestParam(required = false) DeletionRole deletedByRole,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          LocalDateTime from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          LocalDateTime to,
      @RequestParam(required = false) String q,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      Authentication authentication) {
    RecycleBinFilter filter = new RecycleBinFilter(null, area, deletedByRole, from, to, q);
    Page<DeletedEntry> entries =
        recycleBinService.list(
            DeletionActor.of(authentication), filter, PageRequests.of(page, size));
    Map<UUID, Long> nested = recycleBinService.nestedCounts(entries.getContent());
    return PagedResponse.of(
        entries.map(
            entry ->
                RecycleBinEntryResponse.from(
                    entry, nested.getOrDefault(entry.getCascadeGroupId(), 0L))));
  }

  @GetMapping("/{entryId}/items")
  public ResponseEntity<List<RecycleBinEntryResponse>> groupItems(
      @PathVariable UUID entryId, Authentication authentication) {
    return ResponseEntity.ok(
        recycleBinService.groupItems(entryId, DeletionActor.of(authentication)).stream()
            .map(entry -> RecycleBinEntryResponse.from(entry, 0))
            .toList());
  }

  @PostMapping("/{entryId}/restore")
  public ResponseEntity<Void> restore(@PathVariable UUID entryId, Authentication authentication) {
    recycleBinService.restore(entryId, DeletionActor.of(authentication));
    return ResponseEntity.noContent().build();
  }
}
