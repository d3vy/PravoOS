package com.pravoos.ai.arbitr;

import java.util.Optional;

public interface ArbitrCaseProvider {

    boolean isEnabled();

    Optional<ArbitrCaseData> fetchCase(String arbitrCaseNumber);
}
