package com.pravoos.ai.document.api;

import java.time.LocalDate;

public record LegislationRef(String actCanonical, String articleNumber, LocalDate editionDate) {}
