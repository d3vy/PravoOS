package com.pravoos.user.service;

import com.pravoos.user.exception.ProfileNotFoundException;
import com.pravoos.user.model.dto.NotificationSettingsResponse;
import com.pravoos.user.model.dto.UpdateNotificationSettingsRequest;
import com.pravoos.user.model.entity.LawyerProfile;
import com.pravoos.user.model.entity.User;
import com.pravoos.user.repository.LawyerProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private LawyerProfileRepository lawyerProfileRepository;

    private UserService service;

    @BeforeEach
    void setUp() {
        service = new UserService(lawyerProfileRepository);
    }

    @Test
    void getNotificationSettings_returnsDefaults_andTelegramLinkedFlag() {
        UUID userId = UUID.randomUUID();
        LawyerProfile profile = profile(userId, 555L);
        when(lawyerProfileRepository.findByUserIdWithUser(userId)).thenReturn(Optional.of(profile));

        NotificationSettingsResponse response = service.getNotificationSettings(userId);

        assertThat(response.loginAlertEmail()).isTrue();
        assertThat(response.loginAlertTelegram()).isFalse();
        assertThat(response.telegramLinked()).isTrue();
    }

    @Test
    void getNotificationSettings_reportsTelegramNotLinked_whenChatIdMissing() {
        UUID userId = UUID.randomUUID();
        when(lawyerProfileRepository.findByUserIdWithUser(userId)).thenReturn(Optional.of(profile(userId, null)));

        assertThat(service.getNotificationSettings(userId).telegramLinked()).isFalse();
    }

    @Test
    void getNotificationSettings_throws_whenProfileMissing() {
        UUID userId = UUID.randomUUID();
        when(lawyerProfileRepository.findByUserIdWithUser(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getNotificationSettings(userId))
                .isInstanceOf(ProfileNotFoundException.class);
    }

    @Test
    void updateNotificationSettings_persistsChannelsOnUser() {
        UUID userId = UUID.randomUUID();
        LawyerProfile profile = profile(userId, 555L);
        when(lawyerProfileRepository.findByUserIdWithUser(userId)).thenReturn(Optional.of(profile));

        NotificationSettingsResponse response = service.updateNotificationSettings(
                userId, new UpdateNotificationSettingsRequest(false, true));

        assertThat(profile.getUser().isLoginAlertEmail()).isFalse();
        assertThat(profile.getUser().isLoginAlertTelegram()).isTrue();
        assertThat(response.loginAlertEmail()).isFalse();
        assertThat(response.loginAlertTelegram()).isTrue();
    }

    @Test
    void updateNotificationSettings_throws_whenProfileMissing() {
        UUID userId = UUID.randomUUID();
        when(lawyerProfileRepository.findByUserIdWithUser(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateNotificationSettings(
                userId, new UpdateNotificationSettingsRequest(true, true)))
                .isInstanceOf(ProfileNotFoundException.class);
    }

    private LawyerProfile profile(UUID userId, Long telegramChatId) {
        User user = new User();
        user.setId(userId);
        user.setEmail("lawyer@example.com");

        LawyerProfile profile = new LawyerProfile();
        profile.setUser(user);
        profile.setFullName("Иван Юрист");
        profile.setTelegramChatId(telegramChatId);
        return profile;
    }
}
