package com.pravoos.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pravoos.user.identity.internal.config.MfaProperties;
import com.pravoos.user.identity.internal.dto.MfaSetupResponse;
import com.pravoos.user.identity.internal.dto.MfaStatusResponse;
import com.pravoos.user.identity.internal.model.entity.UserMfa;
import com.pravoos.user.identity.internal.repository.UserMfaRepository;
import com.pravoos.user.identity.internal.security.TotpGenerator;
import com.pravoos.user.identity.internal.service.MfaService;
import com.pravoos.user.identity.model.entity.User;
import com.pravoos.user.identity.model.enums.UserRole;
import com.pravoos.user.identity.repository.UserRepository;
import com.pravoos.user.shared.exception.MfaException;
import com.pravoos.user.shared.exception.ProfileNotFoundException;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MfaServiceTest {

  @Mock private UserMfaRepository userMfaRepository;
  @Mock private UserRepository userRepository;
  @Mock private TotpGenerator totpGenerator;

  private MfaProperties properties;
  private MfaService service;

  @BeforeEach
  void setUp() {
    properties = new MfaProperties("PravoOS", true, Duration.ofMinutes(5), 5);
    service = new MfaService(userMfaRepository, userRepository, totpGenerator, properties);
  }

  @Test
  void status_returnsEnabledAndMandatory_forAdmin() {
    UUID userId = UUID.randomUUID();
    when(userRepository.findById(userId)).thenReturn(Optional.of(user(userId, UserRole.ADMIN)));
    when(userMfaRepository.existsByUserIdAndEnabledTrue(userId)).thenReturn(true);

    MfaStatusResponse response = service.status(userId);

    assertThat(response.enabled()).isTrue();
    assertThat(response.mandatory()).isTrue();
  }

  @Test
  void status_isNotMandatory_forLawyer() {
    UUID userId = UUID.randomUUID();
    when(userRepository.findById(userId)).thenReturn(Optional.of(user(userId, UserRole.LAWYER)));
    when(userMfaRepository.existsByUserIdAndEnabledTrue(userId)).thenReturn(false);

    MfaStatusResponse response = service.status(userId);

    assertThat(response.enabled()).isFalse();
    assertThat(response.mandatory()).isFalse();
  }

  @Test
  void status_throws_whenUserNotFound() {
    UUID userId = UUID.randomUUID();
    when(userRepository.findById(userId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.status(userId)).isInstanceOf(ProfileNotFoundException.class);
  }

  @Test
  void setup_throws_whenAlreadyEnabled() {
    UUID userId = UUID.randomUUID();
    when(userRepository.findById(userId)).thenReturn(Optional.of(user(userId, UserRole.LAWYER)));
    when(userMfaRepository.existsByUserIdAndEnabledTrue(userId)).thenReturn(true);

    assertThatThrownBy(() -> service.setup(userId))
        .isInstanceOf(MfaException.class)
        .hasFieldOrPropertyWithValue("code", "MFA_ALREADY_ENABLED");
  }

  @Test
  void setup_createsPendingSecret_andReturnsOtpAuthUri() {
    UUID userId = UUID.randomUUID();
    User user = user(userId, UserRole.LAWYER);
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(userMfaRepository.existsByUserIdAndEnabledTrue(userId)).thenReturn(false);
    when(userMfaRepository.findByUserId(userId)).thenReturn(Optional.empty());
    when(totpGenerator.generateSecret()).thenReturn("SECRET123");
    when(totpGenerator.otpAuthUri("SECRET123", user.getEmail(), "PravoOS"))
        .thenReturn("otpauth://totp/uri");

    MfaSetupResponse response = service.setup(userId);

    assertThat(response.secret()).isEqualTo("SECRET123");
    assertThat(response.otpauthUri()).isEqualTo("otpauth://totp/uri");
    ArgumentCaptor<UserMfa> captor = ArgumentCaptor.forClass(UserMfa.class);
    verify(userMfaRepository).save(captor.capture());
    UserMfa saved = captor.getValue();
    assertThat(saved.getUserId()).isEqualTo(userId);
    assertThat(saved.getSecret()).isEqualTo("SECRET123");
    assertThat(saved.isEnabled()).isFalse();
    assertThat(saved.getConfirmedAt()).isNull();
  }

  @Test
  void setup_reusesExistingPendingRecord() {
    UUID userId = UUID.randomUUID();
    User user = user(userId, UserRole.LAWYER);
    UserMfa existing = new UserMfa();
    existing.setUserId(userId);
    existing.setSecret("OLD_SECRET");
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(userMfaRepository.existsByUserIdAndEnabledTrue(userId)).thenReturn(false);
    when(userMfaRepository.findByUserId(userId)).thenReturn(Optional.of(existing));
    when(totpGenerator.generateSecret()).thenReturn("NEW_SECRET");
    when(totpGenerator.otpAuthUri(anyString(), anyString(), anyString())).thenReturn("uri");

    service.setup(userId);

    ArgumentCaptor<UserMfa> captor = ArgumentCaptor.forClass(UserMfa.class);
    verify(userMfaRepository).save(captor.capture());
    assertThat(captor.getValue()).isSameAs(existing);
    assertThat(captor.getValue().getSecret()).isEqualTo("NEW_SECRET");
  }

  @Test
  void enable_throws_whenNoPendingSetup() {
    UUID userId = UUID.randomUUID();
    when(userMfaRepository.findByUserId(userId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.enable(userId, "123456"))
        .isInstanceOf(MfaException.class)
        .hasFieldOrPropertyWithValue("code", "MFA_NOT_PENDING");
  }

  @Test
  void enable_throws_whenAlreadyEnabled() {
    UUID userId = UUID.randomUUID();
    UserMfa mfa = new UserMfa();
    mfa.setUserId(userId);
    mfa.setEnabled(true);
    when(userMfaRepository.findByUserId(userId)).thenReturn(Optional.of(mfa));

    assertThatThrownBy(() -> service.enable(userId, "123456"))
        .isInstanceOf(MfaException.class)
        .hasFieldOrPropertyWithValue("code", "MFA_ALREADY_ENABLED");
  }

  @Test
  void enable_throws_whenCodeInvalid() {
    UUID userId = UUID.randomUUID();
    UserMfa mfa = new UserMfa();
    mfa.setUserId(userId);
    mfa.setSecret("SECRET");
    mfa.setEnabled(false);
    when(userMfaRepository.findByUserId(userId)).thenReturn(Optional.of(mfa));
    when(totpGenerator.verify("SECRET", "000000")).thenReturn(false);

    assertThatThrownBy(() -> service.enable(userId, "000000"))
        .isInstanceOf(MfaException.class)
        .hasFieldOrPropertyWithValue("code", "MFA_INVALID_CODE");
    assertThat(mfa.isEnabled()).isFalse();
  }

  @Test
  void enable_marksMfaEnabled_whenCodeValid() {
    UUID userId = UUID.randomUUID();
    UserMfa mfa = new UserMfa();
    mfa.setUserId(userId);
    mfa.setSecret("SECRET");
    mfa.setEnabled(false);
    when(userMfaRepository.findByUserId(userId)).thenReturn(Optional.of(mfa));
    when(totpGenerator.verify("SECRET", "123456")).thenReturn(true);

    service.enable(userId, "123456");

    assertThat(mfa.isEnabled()).isTrue();
    assertThat(mfa.getConfirmedAt()).isNotNull();
  }

  @Test
  void disable_throws_whenMandatoryForRole() {
    UUID userId = UUID.randomUUID();
    when(userRepository.findById(userId)).thenReturn(Optional.of(user(userId, UserRole.ADMIN)));

    assertThatThrownBy(() -> service.disable(userId, "123456"))
        .isInstanceOf(MfaException.class)
        .hasFieldOrPropertyWithValue("code", "MFA_MANDATORY");
    verify(userMfaRepository, never()).deleteByUserId(any());
  }

  @Test
  void disable_throws_whenNotEnabled() {
    UUID userId = UUID.randomUUID();
    when(userRepository.findById(userId)).thenReturn(Optional.of(user(userId, UserRole.LAWYER)));
    when(userMfaRepository.findByUserId(userId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.disable(userId, "123456"))
        .isInstanceOf(MfaException.class)
        .hasFieldOrPropertyWithValue("code", "MFA_NOT_ENABLED");
  }

  @Test
  void disable_throws_whenCodeInvalid() {
    UUID userId = UUID.randomUUID();
    UserMfa mfa = new UserMfa();
    mfa.setUserId(userId);
    mfa.setSecret("SECRET");
    mfa.setEnabled(true);
    when(userRepository.findById(userId)).thenReturn(Optional.of(user(userId, UserRole.LAWYER)));
    when(userMfaRepository.findByUserId(userId)).thenReturn(Optional.of(mfa));
    when(totpGenerator.verify("SECRET", "000000")).thenReturn(false);

    assertThatThrownBy(() -> service.disable(userId, "000000"))
        .isInstanceOf(MfaException.class)
        .hasFieldOrPropertyWithValue("code", "MFA_INVALID_CODE");
    verify(userMfaRepository, never()).deleteByUserId(any());
  }

  @Test
  void disable_deletesRecord_whenCodeValid() {
    UUID userId = UUID.randomUUID();
    UserMfa mfa = new UserMfa();
    mfa.setUserId(userId);
    mfa.setSecret("SECRET");
    mfa.setEnabled(true);
    when(userRepository.findById(userId)).thenReturn(Optional.of(user(userId, UserRole.LAWYER)));
    when(userMfaRepository.findByUserId(userId)).thenReturn(Optional.of(mfa));
    when(totpGenerator.verify("SECRET", "123456")).thenReturn(true);

    service.disable(userId, "123456");

    verify(userMfaRepository).deleteByUserId(userId);
  }

  @Test
  void isMfaEnabled_delegatesToRepository() {
    UUID userId = UUID.randomUUID();
    when(userMfaRepository.existsByUserIdAndEnabledTrue(userId)).thenReturn(true);

    assertThat(service.isMfaEnabled(userId)).isTrue();
  }

  @Test
  void verifyLoginCode_returnsFalse_whenNoMfaRecord() {
    UUID userId = UUID.randomUUID();
    when(userMfaRepository.findByUserId(userId)).thenReturn(Optional.empty());

    assertThat(service.verifyLoginCode(userId, "123456")).isFalse();
  }

  @Test
  void verifyLoginCode_returnsFalse_whenMfaNotEnabled() {
    UUID userId = UUID.randomUUID();
    UserMfa mfa = new UserMfa();
    mfa.setUserId(userId);
    mfa.setSecret("SECRET");
    mfa.setEnabled(false);
    when(userMfaRepository.findByUserId(userId)).thenReturn(Optional.of(mfa));

    assertThat(service.verifyLoginCode(userId, "123456")).isFalse();
  }

  @Test
  void verifyLoginCode_delegatesToTotpGenerator_whenEnabled() {
    UUID userId = UUID.randomUUID();
    UserMfa mfa = new UserMfa();
    mfa.setUserId(userId);
    mfa.setSecret("SECRET");
    mfa.setEnabled(true);
    when(userMfaRepository.findByUserId(userId)).thenReturn(Optional.of(mfa));
    when(totpGenerator.verify("SECRET", "123456")).thenReturn(true);

    assertThat(service.verifyLoginCode(userId, "123456")).isTrue();
  }

  private User user(UUID id, UserRole role) {
    User user = new User();
    user.setId(id);
    user.setRole(role);
    user.setEmail(id + "@example.com");
    return user;
  }
}
