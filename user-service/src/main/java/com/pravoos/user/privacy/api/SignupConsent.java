package com.pravoos.user.privacy.api;

import java.time.LocalDateTime;

public record SignupConsent(
    String policyVersion,
    boolean crossBorderAccepted,
    boolean marketingAccepted,
    LocalDateTime grantedAt,
    String ipAddress,
    String userAgent) {}
