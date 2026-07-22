package com.pravoos.ai.practice.internal.controller;

import com.pravoos.ai.practice.internal.dto.CreateSavedViewRequest;
import com.pravoos.ai.practice.internal.dto.SavedViewResponse;
import com.pravoos.ai.practice.internal.dto.UpdateSavedViewRequest;
import com.pravoos.ai.practice.internal.model.SavedViewScope;
import com.pravoos.ai.practice.internal.service.SavedViewService;
import com.pravoos.common.web.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/ai/saved-views")
public class SavedViewController {

    private final SavedViewService savedViewService;

    public SavedViewController(SavedViewService savedViewService) {
        this.savedViewService = savedViewService;
    }

    @GetMapping
    public ResponseEntity<List<SavedViewResponse>> list(@RequestParam SavedViewScope scope,
                                                        Authentication authentication) {
        return ResponseEntity.ok(savedViewService.findVisible(
                scope,
                SecurityUtils.currentUserId(authentication),
                SecurityUtils.currentOrgIds(authentication)));
    }

    @PostMapping
    public ResponseEntity<SavedViewResponse> create(@Valid @RequestBody CreateSavedViewRequest request,
                                                    Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(savedViewService.create(
                request,
                SecurityUtils.currentUserId(authentication),
                SecurityUtils.currentOrgIds(authentication)));
    }

    @PutMapping("/{viewId}")
    public ResponseEntity<SavedViewResponse> update(@PathVariable UUID viewId,
                                                    @Valid @RequestBody UpdateSavedViewRequest request,
                                                    Authentication authentication) {
        return ResponseEntity.ok(savedViewService.update(
                viewId,
                request,
                SecurityUtils.currentUserId(authentication),
                SecurityUtils.currentOrgIds(authentication)));
    }

    @DeleteMapping("/{viewId}")
    public ResponseEntity<Void> delete(@PathVariable UUID viewId, Authentication authentication) {
        savedViewService.delete(viewId, SecurityUtils.currentUserId(authentication));
        return ResponseEntity.noContent().build();
    }
}
