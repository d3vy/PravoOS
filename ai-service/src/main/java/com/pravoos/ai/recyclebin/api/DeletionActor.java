package com.pravoos.ai.recyclebin.api;

import com.pravoos.common.web.SecurityUtils;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

public record DeletionActor(UUID userId, DeletionRole role, UUID orgId, List<UUID> orgIds) {

  private static final String ROLE_PREFIX = "ROLE_";

  public DeletionActor {
    orgIds = orgIds == null ? List.of() : List.copyOf(orgIds);
  }

  public static DeletionActor of(Authentication authentication) {
    List<UUID> orgIds = SecurityUtils.currentOrgIds(authentication);
    return new DeletionActor(
        SecurityUtils.currentUserId(authentication),
        resolveRole(authentication),
        orgIds.size() == 1 ? orgIds.get(0) : null,
        orgIds);
  }

  public static DeletionActor system() {
    return new DeletionActor(SYSTEM_ACTOR_ID, DeletionRole.SYSTEM, null, List.of());
  }

  public boolean isAdmin() {
    return role == DeletionRole.ADMIN;
  }

  private static final UUID SYSTEM_ACTOR_ID = new UUID(0L, 0L);

  private static DeletionRole resolveRole(Authentication authentication) {
    return authentication.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .map(
            authority ->
                authority.startsWith(ROLE_PREFIX)
                    ? authority.substring(ROLE_PREFIX.length())
                    : authority)
        .map(DeletionActor::toRole)
        .filter(java.util.Objects::nonNull)
        .findFirst()
        .orElse(DeletionRole.SYSTEM);
  }

  private static DeletionRole toRole(String authority) {
    for (DeletionRole candidate : DeletionRole.values()) {
      if (candidate.name().equals(authority)) {
        return candidate;
      }
    }
    return null;
  }
}
