package com.pravoos.ai.document.api;

import java.time.LocalDate;

public record RetrievedChunk(
    String content,
    String documentTitle,
    double score,
    boolean legislation,
    String actCanonical,
    String articleNumber,
    LocalDate editionDate) {}
