package com.pravoos.ai.document.internal.repository;

import java.time.LocalDate;

public record ChunkMatch(
        String content,
        String documentTitle,
        double distance,
        boolean legislation,
        String actCanonical,
        String articleNumber,
        LocalDate editionDate) {}
