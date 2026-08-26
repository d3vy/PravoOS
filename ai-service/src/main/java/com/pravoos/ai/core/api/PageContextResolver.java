package com.pravoos.ai.core.api;

import java.util.List;
import java.util.UUID;

public interface PageContextResolver {

  PageContextScope resolve(String entityType, UUID entityId, UUID lawyerId, List<UUID> orgIds);
}
