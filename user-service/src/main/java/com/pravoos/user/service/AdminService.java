package com.pravoos.user.service;

import com.pravoos.user.model.dto.LawyerProfileResponse;
import com.pravoos.user.model.entity.LawyerProfile;
import com.pravoos.user.model.entity.User;
import com.pravoos.user.model.enums.UserRole;
import com.pravoos.user.model.enums.UserStatus;
import com.pravoos.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AdminService {

    private final UserRepository userRepository;

    public AdminService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<LawyerProfileResponse> getActiveLawyers() {
        return userRepository.findByRoleAndStatusWithProfile(UserRole.LAWYER, UserStatus.ACTIVE)
                .stream()
                .map(this::toLawyerProfileResponse)
                .toList();
    }

    private LawyerProfileResponse toLawyerProfileResponse(User user) {
        LawyerProfile profile = user.getLawyerProfile();
        return new LawyerProfileResponse(
                user.getId(),
                user.getEmail(),
                profile != null ? profile.getFullName() : null,
                profile != null ? profile.getBarNumber() : null,
                profile != null ? profile.getSpecialization() : null,
                profile != null ? profile.getPhone() : null
        );
    }
}
