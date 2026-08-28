package com.pravoos.ai.core.api;

import java.util.Collection;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

public enum AiActorRole {
  LAWYER,
  ADMIN;

  private static final String ROLE_PREFIX = "ROLE_";

  public static AiActorRole of(Authentication authentication) {
    Collection<? extends GrantedAuthority> authorities =
        authentication == null ? java.util.List.of() : authentication.getAuthorities();
    boolean lawyer = false;
    for (GrantedAuthority granted : authorities) {
      String authority = granted.getAuthority();
      String role =
          authority.startsWith(ROLE_PREFIX) ? authority.substring(ROLE_PREFIX.length()) : authority;
      if (ADMIN.name().equals(role)) {
        return ADMIN;
      }
      if (LAWYER.name().equals(role)) {
        lawyer = true;
      }
    }
    if (lawyer) {
      return LAWYER;
    }
    throw new IllegalArgumentException("Authentication carries no AI-capable role");
  }
}
