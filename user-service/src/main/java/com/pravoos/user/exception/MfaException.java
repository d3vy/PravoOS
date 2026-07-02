package com.pravoos.user.exception;

import org.springframework.http.HttpStatus;

public class MfaException extends PravoosException {

    private MfaException(String message, HttpStatus status, String code) {
        super(message, status, code);
    }

    public static MfaException invalidCode() {
        return new MfaException("Неверный код подтверждения", HttpStatus.UNAUTHORIZED, "MFA_INVALID_CODE");
    }

    public static MfaException invalidChallenge() {
        return new MfaException("Сессия подтверждения истекла. Войдите заново.", HttpStatus.UNAUTHORIZED, "MFA_INVALID_CHALLENGE");
    }

    public static MfaException notPending() {
        return new MfaException("Двухфакторная аутентификация не инициализирована", HttpStatus.CONFLICT, "MFA_NOT_PENDING");
    }

    public static MfaException alreadyEnabled() {
        return new MfaException("Двухфакторная аутентификация уже включена", HttpStatus.CONFLICT, "MFA_ALREADY_ENABLED");
    }

    public static MfaException notEnabled() {
        return new MfaException("Двухфакторная аутентификация не включена", HttpStatus.CONFLICT, "MFA_NOT_ENABLED");
    }

    public static MfaException mandatory() {
        return new MfaException("Двухфакторная аутентификация обязательна для администраторов и не может быть отключена",
                HttpStatus.FORBIDDEN, "MFA_MANDATORY");
    }
}
