package com.pravoos.ai.document.api;

import java.util.UUID;

public record DocumentRef(UUID id, UUID caseId, UUID uploadedBy, String title) {}
