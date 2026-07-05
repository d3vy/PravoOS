package com.pravoos.user.service;

import com.pravoos.user.exception.ProfileNotFoundException;
import com.pravoos.user.model.dto.LawyerProfileResponse;
import com.pravoos.user.model.dto.NotificationSettingsResponse;
import com.pravoos.user.model.dto.UpdateNotificationSettingsRequest;
import com.pravoos.user.model.dto.UpdateProfileRequest;
import com.pravoos.user.model.entity.LawyerProfile;
import com.pravoos.user.model.entity.User;
import com.pravoos.user.repository.LawyerProfileRepository;
import com.pravoos.user.repository.UserRepository;
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
        user.setCaseMessageEmail(request.caseMessageEmail());
        user.setCaseMessageTelegram(request.caseMessageTelegram());
        return toSettingsResponse(user);
    }

    private NotificationSettingsResponse toSettingsResponse(User user) {
        boolean telegramLinked = lawyerProfileRepository.findByUserIdWithUser(user.getId())
                .map(profile -> profile.getTelegramChatId() != null)
                .orElse(false);
        return new NotificationSettingsResponse(
                user.isLoginAlertEmail(),
                user.isLoginAlertTelegram(),
                user.isCaseMessageEmail(),
                user.isCaseMessageTelegram(),
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
