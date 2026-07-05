package com.pravoos.user.service;

import com.pravoos.user.identity.internal.service.UserService;
import com.pravoos.user.shared.exception.ProfileNotFoundException;
import com.pravoos.user.model.dto.NotificationSettingsResponse;
import com.pravoos.user.model.dto.UpdateNotificationSettingsRequest;
import com.pravoos.user.identity.internal.model.entity.LawyerProfile;
import com.pravoos.user.identity.internal.model.entity.User;
import com.pravoos.user.identity.internal.repository.LawyerProfileRepository;
import com.pravoos.user.identity.internal.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private LawyerProfileRepository lawyerProfileRepository;
    @Mock private UserRepository userRepository;

    private UserService service;

    @BeforeEach
    void setUp() {
        service = new UserService(lawyerProfileRepository, userRepository);
    }

    @Test
    void getNotificationSettings_returnsDefaults_andTelegramLinkedFlag() {
        UUID userId = UUID.randomUUID();
        User user = user(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(lawyerProfileRepository.findByUserIdWithUser(userId))
                .thenReturn(Optional.of(profile(user, 555L)));

        NotificationSettingsResponse response = service.getNotificationSettings(userId);

        assertThat(response.loginAlertEmail()).isTrue();
        assertThat(response.loginAlertTelegram()).isFalse();
        assertThat(response.caseMessageEmail()).isTrue();
        assertThat(response.caseMessageTelegram()).isFalse();
        assertThat(response.telegramLinked()).isTrue();
    }

    @Test
    void getNotificationSettings_reportsTelegramNotLinked_forClientWithoutProfile() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user(userId)));
        when(lawyerProfileRepository.findByUserIdWithUser(userId)).thenReturn(Optional.empty());

        assertThat(service.getNotificationSettings(userId).telegramLinked()).isFalse();
    }

    @Test
    void getNotificationSettings_throws_whenUserMissing() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getNotificationSettings(userId))
                .isInstanceOf(ProfileNotFoundException.class);
    }

    @Test
    void updateNotificationSettings_persistsAllChannels() {
        UUID userId = UUID.randomUUID();
        User user = user(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        lenient().when(lawyerProfileRepository.findByUserIdWithUser(userId))
                .thenReturn(Optional.of(profile(user, 555L)));

        NotificationSettingsResponse response = service.updateNotificationSettings(
                userId, new UpdateNotificationSettingsRequest(false, true, false, true));

        assertThat(user.isLoginAlertEmail()).isFalse();
        assertThat(user.isLoginAlertTelegram()).isTrue();
        assertThat(user.isCaseMessageEmail()).isFalse();
        assertThat(user.isCaseMessageTelegram()).isTrue();
        assertThat(response.caseMessageTelegram()).isTrue();
    }

    @Test
    void updateNotificationSettings_throws_whenUserMissing() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateNotificationSettings(
                userId, new UpdateNotificationSettingsRequest(true, true, true, true)))
                .isInstanceOf(ProfileNotFoundException.class);
    }

    private User user(UUID userId) {
        User user = new User();
        user.setId(userId);
        user.setEmail("lawyer@example.com");
        return user;
    }

    private LawyerProfile profile(User user, Long telegramChatId) {
        LawyerProfile profile = new LawyerProfile();
        profile.setUser(user);
        profile.setFullName("Иван Юрист");
        profile.setTelegramChatId(telegramChatId);
        return profile;
    }
}
