package com.pravoos.ai.core.internal.dto;

import com.pravoos.ai.shared.model.enums.CitationStatus;

import java.util.List;

public record CitationCheckResult(
        List<CitationCheck> citations,
        int total,
        int verified,
        int notFound,
        int unverified
) {
    public static CitationCheckResult of(List<CitationCheck> citations) {
        int verified = (int) citations.stream().filter(c -> c.status() == CitationStatus.VERIFIED).count();
        int notFound = (int) citations.stream().filter(c -> c.status() == CitationStatus.NOT_FOUND).count();
        int unverified = (int) citations.stream().filter(c -> c.status() == CitationStatus.UNVERIFIED).count();
        return new CitationCheckResult(citations, citations.size(), verified, notFound, unverified);
    }
}
