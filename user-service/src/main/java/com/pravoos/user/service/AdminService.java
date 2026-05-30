package com.pravoos.user.service;

import com.pravoos.user.model.dto.UserResponse;
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
    public List<UserResponse> getActiveLawyers() {
        return userRepository.findByRoleAndStatusWithProfile(UserRole.LAWYER, UserStatus.ACTIVE)
                .stream()
                .map(this::toUserResponse)
                .toList();
    }

    private UserResponse toUserResponse(User user) {
        String fullName = user.getLawyerProfile() != null
                ? user.getLawyerProfile().getFullName()
                : null;
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getRole(),
                user.getStatus(),
                user.getCreatedAt(),
                fullName
        );
    }
}
