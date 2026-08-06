package com.pravoos.user.collaboration.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pravoos.user.collaboration.internal.config.TelegramProperties;
import com.pravoos.user.collaboration.internal.dto.TelegramLinkResponse;
import com.pravoos.user.collaboration.internal.model.entity.TelegramLinkCode;
import com.pravoos.user.collaboration.internal.repository.TelegramLinkCodeRepository;
import com.pravoos.user.identity.model.entity.LawyerProfile;
import com.pravoos.user.identity.repository.LawyerProfileRepository;
import com.pravoos.user.shared.exception.InvalidTelegramLinkCodeException;
import com.pravoos.user.shared.exception.ProfileNotFoundException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TelegramLinkServiceTest {

  @Mock private TelegramLinkCodeRepository linkCodeRepository;
  @Mock private LawyerProfileRepository lawyerProfileRepository;

  private TelegramProperties properties;
  private TelegramLinkService service;

  @BeforeEach
  void setUp() {
    properties = new TelegramProperties("pravoos_bot", Duration.ofMinutes(15));
    service = new TelegramLinkService(linkCodeRepository, lawyerProfileRepository, properties);
  }

  @Test
  void createLinkCode_throwsWhenProfileMissing() {
    UUID userId = UUID.randomUUID();
    when(lawyerProfileRepository.findByUserIdWithUser(userId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.createLinkCode(userId))
        .isInstanceOf(ProfileNotFoundException.class);

    verify(linkCodeRepository, never()).save(any());
  }

  @Test
  void createLinkCode_deletesExistingCodesAndSavesNewOne() {
    UUID userId = UUID.randomUUID();
    when(lawyerProfileRepository.findByUserIdWithUser(userId))
        .thenReturn(Optional.of(lawyerProfile(userId)));

    TelegramLinkResponse response = service.createLinkCode(userId);

    verify(linkCodeRepository).deleteByUserId(userId);
    verify(linkCodeRepository).flush();
    ArgumentCaptor<TelegramLinkCode> captor = ArgumentCaptor.forClass(TelegramLinkCode.class);
    verify(linkCodeRepository).save(captor.capture());
    TelegramLinkCode saved = captor.getValue();
    assertThat(saved.getUserId()).isEqualTo(userId);
    assertThat(saved.getCode()).isNotBlank();
    assertThat(response.code()).isEqualTo(saved.getCode());
    assertThat(response.deepLink()).isEqualTo("https://t.me/pravoos_bot?start=" + saved.getCode());
    assertThat(response.expiresAt()).isEqualTo(saved.getExpiresAt());
  }

  @Test
  void createLinkCode_generatesDistinctCodesAcrossCalls() {
    UUID userId = UUID.randomUUID();
    when(lawyerProfileRepository.findByUserIdWithUser(userId))
        .thenReturn(Optional.of(lawyerProfile(userId)));

    TelegramLinkResponse first = service.createLinkCode(userId);
    TelegramLinkResponse second = service.createLinkCode(userId);

    assertThat(first.code()).isNotEqualTo(second.code());
  }

  @Test
  void bind_throwsWhenCodeUnknown() {
    when(linkCodeRepository.findById("bad-code")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.bind("bad-code", 123L))
        .isInstanceOf(InvalidTelegramLinkCodeException.class);
  }

  @Test
  void bind_throwsAndDeletesWhenCodeExpired() {
    LocalDateTime expired = LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1);
    TelegramLinkCode code =
        new TelegramLinkCode("expired-code", UUID.randomUUID(), expired, expired.minusMinutes(15));
    when(linkCodeRepository.findById("expired-code")).thenReturn(Optional.of(code));

    assertThatThrownBy(() -> service.bind("expired-code", 123L))
        .isInstanceOf(InvalidTelegramLinkCodeException.class);

    verify(linkCodeRepository).delete(code);
    verify(lawyerProfileRepository, never()).findByUserIdWithUser(any());
  }

  @Test
  void bind_throwsWhenProfileMissing() {
    UUID userId = UUID.randomUUID();
    LocalDateTime notExpired = LocalDateTime.now(ZoneOffset.UTC).plusMinutes(10);
    TelegramLinkCode code =
        new TelegramLinkCode("code", userId, notExpired, notExpired.minusMinutes(5));
    when(linkCodeRepository.findById("code")).thenReturn(Optional.of(code));
    when(lawyerProfileRepository.findByUserIdWithUser(userId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.bind("code", 123L))
        .isInstanceOf(ProfileNotFoundException.class);
  }

  @Test
  void bind_setsChatIdAndDeletesCode() {
    UUID userId = UUID.randomUUID();
    LocalDateTime notExpired = LocalDateTime.now(ZoneOffset.UTC).plusMinutes(10);
    TelegramLinkCode code =
        new TelegramLinkCode("code", userId, notExpired, notExpired.minusMinutes(5));
    when(linkCodeRepository.findById("code")).thenReturn(Optional.of(code));
    LawyerProfile profile = lawyerProfile(userId);
    profile.setFullName("Иван Петров");
    when(lawyerProfileRepository.findByUserIdWithUser(userId)).thenReturn(Optional.of(profile));

    String fullName = service.bind("code", 555L);

    assertThat(profile.getTelegramChatId()).isEqualTo(555L);
    assertThat(fullName).isEqualTo("Иван Петров");
    verify(linkCodeRepository).delete(code);
  }

  @Test
  void unlink_throwsWhenProfileMissing() {
    UUID userId = UUID.randomUUID();
    when(lawyerProfileRepository.findByUserIdWithUser(userId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.unlink(userId)).isInstanceOf(ProfileNotFoundException.class);

    verify(linkCodeRepository, never()).deleteByUserId(any());
  }

  @Test
  void unlink_clearsChatIdAndDeletesCodes() {
    UUID userId = UUID.randomUUID();
    LawyerProfile profile = lawyerProfile(userId);
    profile.setTelegramChatId(999L);
    when(lawyerProfileRepository.findByUserIdWithUser(userId)).thenReturn(Optional.of(profile));

    service.unlink(userId);

    assertThat(profile.getTelegramChatId()).isNull();
    verify(linkCodeRepository).deleteByUserId(userId);
  }

  @Test
  void resolveChatId_returnsEmptyWhenProfileMissing() {
    UUID userId = UUID.randomUUID();
    when(lawyerProfileRepository.findByUserIdWithUser(userId)).thenReturn(Optional.empty());

    assertThat(service.resolveChatId(userId)).isEmpty();
  }

  @Test
  void resolveChatId_returnsChatIdWhenPresent() {
    UUID userId = UUID.randomUUID();
    LawyerProfile profile = lawyerProfile(userId);
    profile.setTelegramChatId(42L);
    when(lawyerProfileRepository.findByUserIdWithUser(userId)).thenReturn(Optional.of(profile));

    assertThat(service.resolveChatId(userId)).contains(42L);
  }

  @Test
  void resolveChatId_returnsEmptyWhenChatIdNotSet() {
    UUID userId = UUID.randomUUID();
    when(lawyerProfileRepository.findByUserIdWithUser(userId))
        .thenReturn(Optional.of(lawyerProfile(userId)));

    assertThat(service.resolveChatId(userId)).isEmpty();
  }

  @Test
  void purgeExpiredCodes_delegatesToRepository() {
    when(linkCodeRepository.deleteByExpiresAtBefore(any())).thenReturn(3);

    service.purgeExpiredCodes();

    verify(linkCodeRepository, times(1)).deleteByExpiresAtBefore(any());
  }

  private static LawyerProfile lawyerProfile(UUID userId) {
    LawyerProfile profile = new LawyerProfile();
    profile.setFullName("Тест Тестов");
    return profile;
  }
}
