package com.pravoos.ai.shared.security;

import com.pravoos.common.web.SecurityUtils;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;

public final class CallerContext {

  private final Authentication authentication;

  public CallerContext(Authentication authentication) {
    this.authentication = authentication;
  }

  public UUID userId() {
    return SecurityUtils.currentUserId(authentication);
  }

  public List<UUID> orgIds() {
    return SecurityUtils.currentOrgIds(authentication);
  }

  public List<UUID> clientIds() {
    return SecurityUtils.currentClientIds(authentication);
  }
}
