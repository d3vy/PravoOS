package com.pravoos.ai.document.api;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentAccess {

  List<DocumentRef> findByIds(Collection<UUID> ids);

  List<DocumentResponse> findChatAttachments(UUID lawyerId);

  List<String> chunkContentsForDocuments(Collection<UUID> ids);

  DocumentRef findForReview(UUID id);

  String extractText(UUID id);

  DocumentSummaryView summaryFor(UUID id);

  DocumentSummaryView regenerateSummary(UUID id, UUID requestedBy);

  boolean knowledgeBaseMentions(String needle);

  Optional<LegislationRef> currentLegislation(String articleNumber, String actCanonical);

  Optional<LegislationRef> supersededLegislation(String articleNumber, String actCanonical);
}
