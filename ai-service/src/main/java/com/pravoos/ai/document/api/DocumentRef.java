package com.pravoos.ai.document.api;

import java.util.UUID;

public record DocumentRef(UUID id, UUID caseId, String title) {}
