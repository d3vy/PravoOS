package com.pravoos.ai.document.api;

import com.pravoos.ai.shared.model.enums.DocumentKind;
import com.pravoos.ai.shared.model.enums.DocumentStatus;
import com.pravoos.ai.shared.model.enums.DocumentSummaryStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record DocumentSummaryView(
    UUID id,
    UUID caseId,
    UUID uploadedBy,
    String title,
    DocumentKind documentKind,
    DocumentStatus status,
    DocumentSummaryStatus summaryStatus,
    String summary,
    List<String> keyPoints,
    LocalDateTime generatedAt) {}
