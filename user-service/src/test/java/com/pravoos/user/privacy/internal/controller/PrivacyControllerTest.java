package com.pravoos.user.privacy.internal.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pravoos.user.privacy.internal.config.PrivacyProperties;
import com.pravoos.user.privacy.internal.model.enums.ConsentPurpose;
import com.pravoos.user.privacy.internal.service.ConsentService;
import com.pravoos.user.privacy.internal.service.PersonalDataService;
import com.pravoos.user.shared.exception.MandatoryConsentException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

@ExtendWith(MockitoExtension.class)
class PrivacyControllerTest {

  @Mock private ConsentService consentService;
  @Mock private PersonalDataService personalDataService;
  @Mock private Authentication authentication;
  @Mock private HttpServletRequest httpRequest;

  private PrivacyController controller;

  @BeforeEach
  void setUp() {
    controller =
        new PrivacyController(
            consentService, personalDataService, new PrivacyProperties("2.0", "PravoOS", 10, 30));
  }

  @Test
  void revokeThrowsMandatoryConsentExceptionForMandatoryPurposeWithoutTouchingService() {
    assertThatThrownBy(() -> controller.revoke(ConsentPurpose.PERSONAL_DATA, authentication))
        .isInstanceOf(MandatoryConsentException.class);

    verify(consentService, never()).revoke(any(), any());
  }

  @Test
  void revokeIsAllowedForCrossBorderTransferConsent() {
    UUID userId = UUID.randomUUID();
    when(authentication.getPrincipal()).thenReturn(userId.toString());

    controller.revoke(ConsentPurpose.CROSS_BORDER_TRANSFER, authentication);

    verify(consentService).revoke(userId, ConsentPurpose.CROSS_BORDER_TRANSFER);
  }

  @Test
  void revokeDelegatesToServiceForOptionalPurpose() {
    UUID userId = UUID.randomUUID();
    when(authentication.getPrincipal()).thenReturn(userId.toString());

    controller.revoke(ConsentPurpose.MARKETING, authentication);

    verify(consentService).revoke(userId, ConsentPurpose.MARKETING);
  }

  @Test
  void grantResolvesClientIpFromHeaderAndDelegatesToService() {
    UUID userId = UUID.randomUUID();
    when(authentication.getPrincipal()).thenReturn(userId.toString());
    when(httpRequest.getHeader("X-Client-Ip")).thenReturn("203.0.113.5");
    when(httpRequest.getHeader("User-Agent")).thenReturn("Mozilla/5.0");

    controller.grant(ConsentPurpose.MARKETING, authentication, httpRequest);

    verify(consentService).grant(userId, ConsentPurpose.MARKETING, "203.0.113.5", "Mozilla/5.0");
  }

  @Test
  void grantFallsBackToRemoteAddrWhenNoClientIpHeader() {
    UUID userId = UUID.randomUUID();
    when(authentication.getPrincipal()).thenReturn(userId.toString());
    when(httpRequest.getHeader("X-Client-Ip")).thenReturn(null);
    when(httpRequest.getRemoteAddr()).thenReturn("10.0.0.9");

    controller.grant(ConsentPurpose.MARKETING, authentication, httpRequest);

    verify(consentService).grant(userId, ConsentPurpose.MARKETING, "10.0.0.9", null);
  }

  @Test
  void policyReturnsResolvedVersionAndOperator() {
    var response = controller.policy();

    assertThat(response.getBody())
        .containsEntry("policyVersion", "2.0")
        .containsEntry("operator", "PravoOS");
  }
}
