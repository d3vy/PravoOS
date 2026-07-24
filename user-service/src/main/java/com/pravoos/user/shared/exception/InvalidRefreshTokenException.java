package com.pravoos.user.shared.exception;

import org.springframework.http.HttpStatus;

public class InvalidRefreshTokenException extends PravoosException {

  public InvalidRefreshTokenException() {
    super("Invalid or expired refresh token", HttpStatus.UNAUTHORIZED);
  }
}
