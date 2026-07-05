package com.pravoos.ai.shared.arbitr;

import java.util.Optional;

public interface ArbitrCaseProvider {

    boolean isEnabled();

    Optional<ArbitrCaseData> fetchCase(String arbitrCaseNumber);
}
