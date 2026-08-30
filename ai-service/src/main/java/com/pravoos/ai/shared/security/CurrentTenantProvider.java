package com.pravoos.ai.shared.security;

import com.pravoos.common.web.SecurityUtils;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentTenantProvider {

  public Optional<UUID> currentOrgId() {
    List<UUID> orgIds =
        SecurityUtils.currentOrgIds(SecurityContextHolder.getContext().getAuthentication());
    return orgIds != null && orgIds.size() == 1 ? Optional.of(orgIds.get(0)) : Optional.empty();
  }
}
