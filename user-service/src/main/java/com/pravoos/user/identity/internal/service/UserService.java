package com.pravoos.user.identity.internal.service;

import com.pravoos.user.identity.api.LawyerProfileResponse;
import com.pravoos.user.identity.internal.dto.NotificationSettingsResponse;
import com.pravoos.user.identity.internal.dto.UpdateNotificationSettingsRequest;
import com.pravoos.user.identity.internal.dto.UpdateProfileRequest;
import com.pravoos.user.identity.model.entity.LawyerProfile;
import com.pravoos.user.identity.model.entity.User;
import com.pravoos.user.identity.repository.LawyerProfileRepository;
import com.pravoos.user.identity.repository.UserRepository;
import com.pravoos.user.shared.exception.ProfileNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UserService {

    private final LawyerProfileRepository lawyerProfileRepository;
    private final UserRepository userRepository;

    public UserService(LawyerProfileRepository lawyerProfileRepository, UserRepository userRepository) {
        this.lawyerProfileRepository = lawyerProfileRepository;
        this.userRepository = userRepository;
    }

    public LawyerProfileResponse getProfile(UUID userId) {
        LawyerProfile profile = lawyerProfileRepository.findByUserIdWithUser(userId)
                .orElseThrow(ProfileNotFoundException::new);
        return toResponse(profile);
    }

    @Transactional
    public LawyerProfileResponse updateProfile(UUID userId, UpdateProfileRequest request) {
        LawyerProfile profile = lawyerProfileRepository.findByUserIdWithUser(userId)
                .orElseThrow(ProfileNotFoundException::new);
        profile.setFullName(request.fullName());
        profile.setSpecialization(request.specialization());
        profile.setPhone(request.phone());
        return toResponse(profile);
    }

    public NotificationSettingsResponse getNotificationSettings(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(ProfileNotFoundException::new);
        return toSettingsResponse(user);
    }

    @Transactional
    public NotificationSettingsResponse updateNotificationSettings(UUID userId,
                                                                   UpdateNotificationSettingsRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(ProfileNotFoundException::new);
        user.setLoginAlertEmail(request.loginAlertEmail());
        user.setLoginAlertTelegram(request.loginAlertTelegram());
        user.setLoginAlertPush(request.loginAlertPush());
        user.setCaseMessageEmail(request.caseMessageEmail());
        user.setCaseMessageTelegram(request.caseMessageTelegram());
        user.setCaseMessagePush(request.caseMessagePush());
        return toSettingsResponse(user);
    }

    private NotificationSettingsResponse toSettingsResponse(User user) {
        boolean telegramLinked = lawyerProfileRepository.findByUserIdWithUser(user.getId())
                .map(profile -> profile.getTelegramChatId() != null)
                .orElse(false);
        return new NotificationSettingsResponse(
                user.isLoginAlertEmail(),
                user.isLoginAlertTelegram(),
                user.isLoginAlertPush(),
                user.isCaseMessageEmail(),
                user.isCaseMessageTelegram(),
                user.isCaseMessagePush(),
                telegramLinked
        );
    }

    private LawyerProfileResponse toResponse(LawyerProfile profile) {
        return new LawyerProfileResponse(
                profile.getUserId(),
                profile.getUser().getEmail(),
                profile.getFullName(),
                profile.getSpecialization(),
                profile.getPhone(),
                profile.getTelegramChatId() != null
        );
    }
}
