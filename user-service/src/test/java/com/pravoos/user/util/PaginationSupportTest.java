package com.pravoos.user.util;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import static org.assertj.core.api.Assertions.assertThat;

class PaginationSupportTest {

    @Test
    void clampsNegativePageToZero() {
        Pageable pageable = PaginationSupport.of(-5, 50);
        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(50);
    }

    @Test
    void usesDefaultSizeForNonPositive() {
        assertThat(PaginationSupport.of(0, 0).getPageSize()).isEqualTo(200);
    }

    @Test
    void capsSizeAtMax() {
        assertThat(PaginationSupport.of(0, 10000).getPageSize()).isEqualTo(500);
    }
}
