package com.pravoos.ai.practice.internal.controller;

import com.pravoos.ai.practice.internal.dto.GlobalSearchResponse;
import com.pravoos.ai.practice.internal.service.SearchService;
import com.pravoos.ai.shared.security.CallerContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai/search")
public class SearchController {

  private final SearchService searchService;

  public SearchController(SearchService searchService) {
    this.searchService = searchService;
  }

  @GetMapping
  public ResponseEntity<GlobalSearchResponse> search(
      @RequestParam(required = false) String q,
      @RequestParam(defaultValue = "true") boolean content,
      CallerContext caller) {
    return ResponseEntity.ok(searchService.search(caller.userId(), q, content));
  }
}
