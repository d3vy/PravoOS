package com.pravoos.user.collaboration.internal.controller;

import com.pravoos.user.collaboration.internal.dto.PortalAcceptRequest;
import com.pravoos.user.collaboration.internal.dto.PortalInvitePreviewResponse;
import com.pravoos.user.collaboration.internal.service.ClientPortalInviteService;
import com.pravoos.user.identity.api.AuthTokens;
import com.pravoos.user.identity.api.LoginResponse;
import com.pravoos.user.shared.exception.TooManyRequestsException;
import com.pravoos.user.shared.service.IpRateLimiter;
import com.pravoos.user.shared.util.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class PortalAuthController {

  private static final int PORTAL_MAX_PER_IP = 5;
  private static final Duration PORTAL_WINDOW = Duration.ofHours(1);

  private final ClientPortalInviteService clientPortalInviteService;
  private final AuthTokens authTokens;
  private final IpRateLimiter ipRateLimiter;

  public PortalAuthController(
      ClientPortalInviteService clientPortalInviteService,
      AuthTokens authTokens,
      IpRateLimiter ipRateLimiter) {
    this.clientPortalInviteService = clientPortalInviteService;
    this.authTokens = authTokens;
    this.ipRateLimiter = ipRateLimiter;
  }

  @GetMapping("/portal/invite")
  public ResponseEntity<PortalInvitePreviewResponse> portalInvitePreview(
      @RequestParam("token") String token) {
    return ResponseEntity.ok(clientPortalInviteService.preview(token));
  }

  @PostMapping("/portal/accept")
  public ResponseEntity<LoginResponse> acceptPortalInvite(
      @Valid @RequestBody PortalAcceptRequest request, HttpServletRequest httpRequest) {
    String clientIp = ClientIpResolver.resolve(httpRequest);
    if (!ipRateLimiter.allow("portal-accept", clientIp, PORTAL_MAX_PER_IP, PORTAL_WINDOW)) {
      throw new TooManyRequestsException();
    }
    UUID userId = clientPortalInviteService.accept(request.token(), request.password());
    return authTokens.authenticate(userId, clientIp, userAgent(httpRequest));
  }

  private static String userAgent(HttpServletRequest request) {
    String userAgent = request.getHeader(HttpHeaders.USER_AGENT);
    if (userAgent == null || userAgent.isBlank()) {
      return null;
    }
    return userAgent.length() > 255 ? userAgent.substring(0, 255) : userAgent;
  }
}
