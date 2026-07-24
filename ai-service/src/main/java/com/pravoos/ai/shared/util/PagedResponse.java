package com.pravoos.ai.shared.util;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;

public final class PagedResponse {

  public static final String TOTAL_COUNT_HEADER = "X-Total-Count";

  private PagedResponse() {}

  public static <T> ResponseEntity<List<T>> of(Page<T> page) {
    return ResponseEntity.ok()
        .header(TOTAL_COUNT_HEADER, String.valueOf(page.getTotalElements()))
        .body(page.getContent());
  }
}
