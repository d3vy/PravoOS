package com.pravoos.ai.core.api;

import com.pravoos.ai.core.api.DocumentResponse;

import java.util.List;
import java.util.UUID;

public interface DocumentQuery {

    List<DocumentResponse> findByCase(UUID caseId);

    List<DocumentResponse> findClientVisibleByCase(UUID caseId);
}
