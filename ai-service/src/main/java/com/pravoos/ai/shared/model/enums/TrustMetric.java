package com.pravoos.ai.shared.model.enums;

public enum TrustMetric {
  GUARD_PASS,
  GUARD_BLOCK,
  CITATION_VERIFIED,
  CITATION_NOT_FOUND,
  CITATION_OUTDATED,
  CITATION_UNVERIFIED;

  public static TrustMetric forCitation(CitationStatus status) {
    return valueOf("CITATION_" + status.name());
  }

  public boolean isCitation() {
    return name().startsWith("CITATION_");
  }
}
