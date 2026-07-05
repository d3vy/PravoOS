package com.pravoos.ai.shared.arbitr;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public class NoopArbitrCaseProvider implements ArbitrCaseProvider {

    private static final Logger log = LoggerFactory.getLogger(NoopArbitrCaseProvider.class);

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public Optional<ArbitrCaseData> fetchCase(String arbitrCaseNumber) {
        log.debug("КАД.Арбитр integration is disabled (no API token configured), skipping case {}", arbitrCaseNumber);
        return Optional.empty();
    }
}
