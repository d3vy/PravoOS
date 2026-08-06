package com.pravoos.user.privacy.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.pravoos.common.web.AiProcessingMode;
import com.pravoos.user.privacy.internal.model.entity.UserConsent;
import com.pravoos.user.privacy.internal.model.enums.ConsentPurpose;
import com.pravoos.user.privacy.internal.repository.UserConsentRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AiProcessingModeAdapterTest {

  @Mock private UserConsentRepository consentRepository;

  private AiProcessingModeAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new AiProcessingModeAdapter(consentRepository);
  }

  @Test
  void resolveMode_returnsCrossBorderWhenActiveConsentExists() {
    UUID userId = UUID.randomUUID();
    when(consentRepository.findByUserIdAndPurposeAndRevokedAtIsNull(
            userId, ConsentPurpose.CROSS_BORDER_TRANSFER))
        .thenReturn(Optional.of(new UserConsent()));

    AiProcessingMode mode = adapter.resolveMode(userId);

    assertThat(mode).isEqualTo(AiProcessingMode.CROSS_BORDER);
  }

  @Test
  void resolveMode_returnsRuOnlyWhenNoActiveConsent() {
    UUID userId = UUID.randomUUID();
    when(consentRepository.findByUserIdAndPurposeAndRevokedAtIsNull(
            userId, ConsentPurpose.CROSS_BORDER_TRANSFER))
        .thenReturn(Optional.empty());

    AiProcessingMode mode = adapter.resolveMode(userId);

    assertThat(mode).isEqualTo(AiProcessingMode.RU_ONLY);
  }
}
