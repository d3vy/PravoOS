package com.pravoos.ai.shared.security;

import com.pravoos.ai.shared.exception.AiProcessingDisabledException;
import com.pravoos.ai.shared.exception.CrossBorderConsentRequiredException;
import com.pravoos.common.web.AiProcessingMode;
import com.pravoos.common.web.OrgContext;
import com.pravoos.common.web.SecurityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class AiProcessingGuard {

  private static final Logger log = LoggerFactory.getLogger(AiProcessingGuard.class);

  private final boolean enforced;

  public AiProcessingGuard(
      @Value("${pravoos.privacy.cross-border-transfer.enforced:true}") boolean enforced) {
    this.enforced = enforced;
  }

  public void ensureRemoteCallAllowed() {
    if (!enforced) {
      return;
    }
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !(authentication.getDetails() instanceof OrgContext)) {
      return;
    }
    AiProcessingMode mode = SecurityUtils.currentAiProcessingMode(authentication);
    if (!mode.allowsAi()) {
      log.warn(
          "Blocked LLM call for user {} with AI processing disabled", authentication.getName());
      throw new AiProcessingDisabledException();
    }
    if (!mode.allowsCrossBorderTransfer()) {
      log.warn(
          "Blocked LLM call without cross-border consent for user {} in mode {}",
          authentication.getName(),
          mode);
      throw new CrossBorderConsentRequiredException();
    }
  }
}
