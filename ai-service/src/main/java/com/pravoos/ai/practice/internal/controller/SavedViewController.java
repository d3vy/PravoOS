package com.pravoos.ai.practice.internal.controller;

import com.pravoos.ai.practice.internal.dto.CreateSavedViewRequest;
import com.pravoos.ai.practice.internal.dto.SavedViewResponse;
import com.pravoos.ai.practice.internal.dto.UpdateSavedViewRequest;
import com.pravoos.ai.practice.internal.model.SavedViewScope;
import com.pravoos.ai.practice.internal.service.SavedViewService;
import com.pravoos.ai.recyclebin.api.DeletionActor;
import com.pravoos.ai.shared.security.CallerContext;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai/saved-views")
public class SavedViewController {

  private final SavedViewService savedViewService;

  public SavedViewController(SavedViewService savedViewService) {
    this.savedViewService = savedViewService;
  }

  @GetMapping
  public ResponseEntity<List<SavedViewResponse>> list(
      @RequestParam SavedViewScope scope, CallerContext caller) {
    return ResponseEntity.ok(savedViewService.findVisible(scope, caller.userId(), caller.orgIds()));
  }

  @PostMapping
  public ResponseEntity<SavedViewResponse> create(
      @Valid @RequestBody CreateSavedViewRequest request, CallerContext caller) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(savedViewService.create(request, caller.userId(), caller.orgIds()));
  }

  @PutMapping("/{viewId}")
  public ResponseEntity<SavedViewResponse> update(
      @PathVariable UUID viewId,
      @Valid @RequestBody UpdateSavedViewRequest request,
      CallerContext caller) {
    return ResponseEntity.ok(
        savedViewService.update(viewId, request, caller.userId(), caller.orgIds()));
  }

  @DeleteMapping("/{viewId}")
  public ResponseEntity<Void> delete(@PathVariable UUID viewId, Authentication authentication) {
    savedViewService.delete(viewId, DeletionActor.of(authentication));
    return ResponseEntity.noContent().build();
  }
}
