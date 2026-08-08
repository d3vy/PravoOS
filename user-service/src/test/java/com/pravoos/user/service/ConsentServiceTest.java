package com.pravoos.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.pravoos.user.identity.api.TokenDenylistService;
import com.pravoos.user.privacy.api.SignupConsent;
import com.pravoos.user.privacy.internal.config.PrivacyProperties;
import com.pravoos.user.privacy.internal.model.entity.UserConsent;
import com.pravoos.user.privacy.internal.model.enums.ConsentPurpose;
import com.pravoos.user.privacy.internal.repository.UserConsentRepository;
import com.pravoos.user.privacy.internal.service.ConsentService;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConsentServiceTest {

  private static final UUID USER_ID = UUID.randomUUID();

  @Mock private UserConsentRepository consentRepository;
  @Mock private TokenDenylistService tokenDenylistService;

  private ConsentService service;

  @BeforeEach
  void setUp() {
    service =
        new ConsentService(
            consentRepository, new PrivacyProperties("2.0", "ООО", 10, 30), tokenDenylistService);
    lenient().when(consentRepository.save(any())).thenAnswer(call -> call.getArgument(0));
  }

  @Test
  void recordSignupConsent_storesMandatoryAndAcceptedOptionalPurposes() {
    when(consentRepository.findByUserIdAndPurposeAndRevokedAtIsNull(eq(USER_ID), any()))
        .thenReturn(Optional.empty());

    service.recordSignupConsent(
        USER_ID,
        new SignupConsent("2.0", true, false, LocalDateTime.now(ZoneOffset.UTC), "1.2.3.4", "UA"));

    ArgumentCaptor<UserConsent> captor = ArgumentCaptor.forClass(UserConsent.class);
    verify(consentRepository, times(2)).save(captor.capture());
    assertThat(captor.getAllValues())
        .extracting(UserConsent::getPurpose)
        .containsExactly(ConsentPurpose.PERSONAL_DATA, ConsentPurpose.CROSS_BORDER_TRANSFER);
    assertThat(captor.getAllValues()).allMatch(consent -> "1.2.3.4".equals(consent.getIpAddress()));
  }

  @Test
  void recordSignupConsent_skipsPurposesThatAreAlreadyActive() {
    when(consentRepository.findByUserIdAndPurposeAndRevokedAtIsNull(
            USER_ID, ConsentPurpose.PERSONAL_DATA))
        .thenReturn(Optional.of(consent(ConsentPurpose.PERSONAL_DATA, "2.0")));

    service.recordSignupConsent(
        USER_ID,
        new SignupConsent("2.0", false, false, LocalDateTime.now(ZoneOffset.UTC), null, null));

    verify(consentRepository, never()).save(any());
  }

  @Test
  void grant_isIdempotentForSamePolicyVersion() {
    UserConsent existing = consent(ConsentPurpose.MARKETING, "2.0");
    when(consentRepository.findByUserIdAndPurposeAndRevokedAtIsNull(
            USER_ID, ConsentPurpose.MARKETING))
        .thenReturn(Optional.of(existing));

    service.grant(USER_ID, ConsentPurpose.MARKETING, "1.2.3.4", "UA");

    verify(consentRepository, never()).save(any());
  }

  @Test
  void grant_revokesOutdatedConsentBeforeStoringNewVersion() {
    UserConsent outdated = consent(ConsentPurpose.PERSONAL_DATA, "1.0");
    when(consentRepository.findByUserIdAndPurposeAndRevokedAtIsNull(
            USER_ID, ConsentPurpose.PERSONAL_DATA))
        .thenReturn(Optional.of(outdated));

    service.grant(USER_ID, ConsentPurpose.PERSONAL_DATA, "1.2.3.4", "UA");

    assertThat(outdated.getRevokedAt()).isNotNull();
    ArgumentCaptor<UserConsent> captor = ArgumentCaptor.forClass(UserConsent.class);
    verify(consentRepository).save(captor.capture());
    assertThat(captor.getValue().getPolicyVersion()).isEqualTo("2.0");
    assertThat(captor.getValue().getSource()).isEqualTo("SETTINGS");
  }

  @Test
  void revoke_marksActiveConsentAsRevoked() {
    UserConsent active = consent(ConsentPurpose.MARKETING, "2.0");
    when(consentRepository.findByUserIdAndPurposeAndRevokedAtIsNull(
            USER_ID, ConsentPurpose.MARKETING))
        .thenReturn(Optional.of(active));

    assertThat(service.revoke(USER_ID, ConsentPurpose.MARKETING)).isTrue();
    assertThat(active.getRevokedAt()).isNotNull();
  }

  @Test
  void revoke_deniesLiveAccessTokens_whenCrossBorderConsentWithdrawn() {
    UserConsent active = consent(ConsentPurpose.CROSS_BORDER_TRANSFER, "2.0");
    when(consentRepository.findByUserIdAndPurposeAndRevokedAtIsNull(
            USER_ID, ConsentPurpose.CROSS_BORDER_TRANSFER))
        .thenReturn(Optional.of(active));

    service.revoke(USER_ID, ConsentPurpose.CROSS_BORDER_TRANSFER);

    verify(tokenDenylistService).revokeAccessTokensFor(USER_ID);
  }

  @Test
  void grant_deniesLiveAccessTokens_whenCrossBorderConsentGiven() {
    when(consentRepository.findByUserIdAndPurposeAndRevokedAtIsNull(
            USER_ID, ConsentPurpose.CROSS_BORDER_TRANSFER))
        .thenReturn(Optional.empty());

    service.grant(USER_ID, ConsentPurpose.CROSS_BORDER_TRANSFER, "1.2.3.4", "UA");

    verify(tokenDenylistService).revokeAccessTokensFor(USER_ID);
  }

  @Test
  void revoke_keepsAccessTokens_forConsentsOutsideTokenClaims() {
    UserConsent active = consent(ConsentPurpose.MARKETING, "2.0");
    when(consentRepository.findByUserIdAndPurposeAndRevokedAtIsNull(
            USER_ID, ConsentPurpose.MARKETING))
        .thenReturn(Optional.of(active));

    service.revoke(USER_ID, ConsentPurpose.MARKETING);

    verify(tokenDenylistService, never()).revokeAccessTokensFor(any());
  }

  @Test
  void revoke_returnsFalseWhenNothingToRevoke() {
    when(consentRepository.findByUserIdAndPurposeAndRevokedAtIsNull(
            USER_ID, ConsentPurpose.MARKETING))
        .thenReturn(Optional.empty());

    assertThat(service.revoke(USER_ID, ConsentPurpose.MARKETING)).isFalse();
  }

  @Test
  void list_returnsConsentHistory() {
    when(consentRepository.findByUserIdOrderByGrantedAtDesc(USER_ID))
        .thenReturn(List.of(consent(ConsentPurpose.PERSONAL_DATA, "2.0")));

    assertThat(service.list(USER_ID))
        .singleElement()
        .satisfies(
            response -> {
              assertThat(response.purpose()).isEqualTo(ConsentPurpose.PERSONAL_DATA);
              assertThat(response.mandatory()).isTrue();
            });
  }

  private UserConsent consent(ConsentPurpose purpose, String version) {
    UserConsent consent = new UserConsent();
    consent.setUserId(USER_ID);
    consent.setPurpose(purpose);
    consent.setPolicyVersion(version);
    consent.setGrantedAt(LocalDateTime.now(ZoneOffset.UTC));
    consent.setSource("SIGNUP");
    return consent;
  }
}
