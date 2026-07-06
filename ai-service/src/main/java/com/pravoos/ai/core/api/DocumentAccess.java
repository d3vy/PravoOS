package com.pravoos.ai.core.api;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface DocumentAccess {

    List<DocumentRef> findByIds(Collection<UUID> ids);

    List<String> chunkContentsForDocuments(Collection<UUID> ids);

    DocumentRef findForReview(UUID id);

    String extractText(UUID id);

    boolean knowledgeBaseMentions(String needle);
}
