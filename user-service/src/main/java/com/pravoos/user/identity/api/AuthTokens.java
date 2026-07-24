package com.pravoos.user.identity.api;

import com.pravoos.user.identity.internal.dto.TokenResponse;
import com.pravoos.user.identity.internal.security.RefreshCookieFactory;
import com.pravoos.user.identity.internal.service.AuthService;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class AuthTokens {

  private final AuthService authService;
  private final RefreshCookieFactory refreshCookieFactory;

  public AuthTokens(AuthService authService, RefreshCookieFactory refreshCookieFactory) {
    this.authService = authService;
    this.refreshCookieFactory = refreshCookieFactory;
  }

  public ResponseEntity<LoginResponse> authenticate(
      UUID userId, String ipAddress, String userAgent) {
    TokenResponse tokens = authService.issueTokensForUser(userId, ipAddress, userAgent);
    AuthResponse auth =
        new AuthResponse(tokens.accessToken(), tokens.userId(), tokens.email(), tokens.role());
    return ResponseEntity.ok()
        .header(
            HttpHeaders.SET_COOKIE, refreshCookieFactory.create(tokens.refreshToken()).toString())
        .body(LoginResponse.authenticated(auth));
  }
}
