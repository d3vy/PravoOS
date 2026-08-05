package com.pravoos.user.privacy.api;

import java.util.UUID;

public interface ConsentRecorder {

  void recordSignupConsent(UUID userId, SignupConsent consent);
}
