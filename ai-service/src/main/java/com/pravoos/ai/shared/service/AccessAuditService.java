package com.pravoos.ai.shared.service;

import com.pravoos.ai.shared.model.entity.AccessAudit;
import com.pravoos.ai.shared.model.enums.AuditAction;
import com.pravoos.ai.shared.repository.jpa.AccessAuditRepository;
import com.pravoos.ai.shared.util.ClientIpResolver;
import com.pravoos.common.web.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

@Service
public class AccessAuditService {

  private static final Logger log = LoggerFactory.getLogger(AccessAuditService.class);
  private static final int USER_AGENT_MAX_LENGTH = 500;
  private static final int RESOURCE_REF_MAX_LENGTH = 64;
  private static final String UNKNOWN_ROLE = "UNKNOWN";

  private final AccessAuditRepository accessAuditRepository;

  public AccessAuditService(AccessAuditRepository accessAuditRepository) {
    this.accessAuditRepository = accessAuditRepository;
  }

  public void record(
      Authentication authentication,
      AuditAction action,
      UUID resourceId,
      HttpServletRequest request) {
    record(authentication, action, resourceId, null, request);
  }

  public void recordRef(
      Authentication authentication,
      AuditAction action,
      String resourceRef,
      HttpServletRequest request) {
    record(authentication, action, null, resourceRef, request);
  }

  public void recordAgentAction(
      UUID actorId, String actorRole, AuditAction action, UUID resourceId, String resourceRef) {
    try {
      AccessAudit entry = new AccessAudit();
      entry.setActorId(actorId);
      entry.setActorRole(actorRole == null ? UNKNOWN_ROLE : actorRole);
      entry.setAction(action.name());
      entry.setResourceType(action.resourceType());
      entry.setResourceId(resourceId);
      entry.setResourceRef(truncateRef(resourceRef));
      accessAuditRepository.save(entry);
    } catch (Exception e) {
      log.warn(
          "Failed to record agent audit for action {} on {}: {}",
          action,
          resourceId != null ? resourceId : resourceRef,
          e.getMessage());
    }
  }

  private void record(
      Authentication authentication,
      AuditAction action,
      UUID resourceId,
      String resourceRef,
      HttpServletRequest request) {
    try {
      AccessAudit entry = new AccessAudit();
      entry.setActorId(SecurityUtils.currentUserId(authentication));
      entry.setActorRole(resolveRole(authentication));
      entry.setAction(action.name());
      entry.setResourceType(action.resourceType());
      entry.setResourceId(resourceId);
      entry.setResourceRef(truncateRef(resourceRef));
      entry.setIpAddress(ClientIpResolver.resolve(request));
      entry.setUserAgent(truncate(request.getHeader("User-Agent")));
      accessAuditRepository.save(entry);
    } catch (Exception e) {
      log.warn(
          "Failed to record access audit for action {} on {}: {}",
          action,
          resourceId != null ? resourceId : resourceRef,
          e.getMessage());
    }
  }

  private String truncateRef(String resourceRef) {
    if (resourceRef == null) {
      return null;
    }
    return resourceRef.length() > RESOURCE_REF_MAX_LENGTH
        ? resourceRef.substring(0, RESOURCE_REF_MAX_LENGTH)
        : resourceRef;
  }

  private String resolveRole(Authentication authentication) {
    return authentication.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .findFirst()
        .map(role -> role.startsWith("ROLE_") ? role.substring("ROLE_".length()) : role)
        .orElse(UNKNOWN_ROLE);
  }

  private String truncate(String userAgent) {
    if (userAgent == null) {
      return null;
    }
    return userAgent.length() > USER_AGENT_MAX_LENGTH
        ? userAgent.substring(0, USER_AGENT_MAX_LENGTH)
        : userAgent;
  }
}
