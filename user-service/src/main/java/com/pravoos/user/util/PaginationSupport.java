package com.pravoos.user.util;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

public final class PaginationSupport {

    private static final int MAX_SIZE = 500;
    private static final int DEFAULT_SIZE = 200;

    private PaginationSupport() {
    }

    public static Pageable of(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        return PageRequest.of(safePage, safeSize);
    }
}
