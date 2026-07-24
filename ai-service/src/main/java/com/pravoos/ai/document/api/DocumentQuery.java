package com.pravoos.ai.document.api;

import java.util.List;
import java.util.UUID;

public interface DocumentQuery {

  List<DocumentResponse> findByCase(UUID caseId);

  List<DocumentResponse> findClientVisibleByCase(UUID caseId);
}
