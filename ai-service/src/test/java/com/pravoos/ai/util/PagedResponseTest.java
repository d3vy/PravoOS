package com.pravoos.ai.util;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PagedResponseTest {

    @Test
    void exposesPageContentAsBodyAndTotalAsHeader() {
        Page<String> page = new PageImpl<>(List.of("a", "b"), PageRequest.of(1, 2), 7);

        ResponseEntity<List<String>> response = PagedResponse.of(page);

        assertThat(response.getBody()).containsExactly("a", "b");
        assertThat(response.getHeaders().getFirst(PagedResponse.TOTAL_COUNT_HEADER)).isEqualTo("7");
    }

    @Test
    void reportsZeroTotalForEmptyPage() {
        Page<String> page = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);

        ResponseEntity<List<String>> response = PagedResponse.of(page);

        assertThat(response.getBody()).isEmpty();
        assertThat(response.getHeaders().getFirst(PagedResponse.TOTAL_COUNT_HEADER)).isEqualTo("0");
    }
}
