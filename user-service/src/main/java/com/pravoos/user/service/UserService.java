package com.pravoos.user.service;

import com.pravoos.user.exception.ProfileNotFoundException;
import com.pravoos.user.model.dto.LawyerProfileResponse;
import com.pravoos.user.model.dto.UpdateProfileRequest;
import com.pravoos.user.model.entity.LawyerProfile;
import com.pravoos.user.repository.LawyerProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UserService {

    private final LawyerProfileRepository lawyerProfileRepository;

    public UserService(LawyerProfileRepository lawyerProfileRepository) {
        this.lawyerProfileRepository = lawyerProfileRepository;
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
        profile.setBarNumber(request.barNumber());
        profile.setSpecialization(request.specialization());
        profile.setPhone(request.phone());
        return toResponse(profile);
    }

    private LawyerProfileResponse toResponse(LawyerProfile profile) {
        return new LawyerProfileResponse(
                profile.getUserId(),
                profile.getUser().getEmail(),
                profile.getFullName(),
                profile.getBarNumber(),
                profile.getSpecialization(),
                profile.getPhone()
        );
    }
}
