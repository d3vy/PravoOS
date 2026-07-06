package com.pravoos.ai.core.api;

import java.util.UUID;

public record DocumentRef(UUID id, UUID caseId, String title) {}
