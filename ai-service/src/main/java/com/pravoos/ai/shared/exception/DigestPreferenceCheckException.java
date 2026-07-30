package com.pravoos.ai.shared.exception;

import org.springframework.http.HttpStatus;

public class DigestPreferenceCheckException extends PravoosException {

  public DigestPreferenceCheckException() {
    super(
        "Не удалось получить настройки дайджеста из user-service",
        HttpStatus.SERVICE_UNAVAILABLE,
        "DIGEST_PREFERENCE_CHECK_FAILED");
  }
}
