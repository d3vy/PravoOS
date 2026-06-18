package com.pravoos.ai.controller;

import com.pravoos.ai.model.dto.GlobalSearchResponse;
import com.pravoos.ai.security.SecurityUtils;
import com.pravoos.ai.service.SearchService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
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
    public ResponseEntity<GlobalSearchResponse> search(@RequestParam(required = false) String q,
                                                       Authentication authentication) {
        return ResponseEntity.ok(searchService.search(SecurityUtils.currentUserId(authentication), q));
    }
}
