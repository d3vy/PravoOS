package com.pravoos.user.privacy.internal.service;

import com.pravoos.common.web.AiProcessingMode;
import com.pravoos.user.identity.api.AiProcessingModeProvider;
import com.pravoos.user.privacy.internal.model.enums.ConsentPurpose;
import com.pravoos.user.privacy.internal.repository.UserConsentRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AiProcessingModeAdapter implements AiProcessingModeProvider {

  private final UserConsentRepository consentRepository;

  public AiProcessingModeAdapter(UserConsentRepository consentRepository) {
    this.consentRepository = consentRepository;
  }

  @Override
  @Transactional(readOnly = true)
  public AiProcessingMode resolveMode(UUID userId) {
    boolean crossBorderConsented =
        consentRepository
            .findByUserIdAndPurposeAndRevokedAtIsNull(userId, ConsentPurpose.CROSS_BORDER_TRANSFER)
            .isPresent();
    return crossBorderConsented ? AiProcessingMode.CROSS_BORDER : AiProcessingMode.RU_ONLY;
  }
}
