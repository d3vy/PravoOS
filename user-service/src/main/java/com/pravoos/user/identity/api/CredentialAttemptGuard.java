package com.pravoos.user.identity.api;

import com.pravoos.user.identity.internal.service.LoginAttemptService;
import com.pravoos.user.shared.exception.AccountLockedException;
import org.springframework.stereotype.Service;

@Service
public class CredentialAttemptGuard {

  private final LoginAttemptService loginAttemptService;

  public CredentialAttemptGuard(LoginAttemptService loginAttemptService) {
    this.loginAttemptService = loginAttemptService;
  }

  public void assertNotLocked(String email) {
    loginAttemptService
        .remainingLockSeconds(email)
        .ifPresent(
            seconds -> {
              throw new AccountLockedException(seconds);
            });
  }

  public void recordFailure(String email) {
    loginAttemptService.recordFailure(email);
  }

  public void recordSuccess(String email) {
    loginAttemptService.reset(email);
  }
}
