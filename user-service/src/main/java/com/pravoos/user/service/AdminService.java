package com.pravoos.user.service;

import com.pravoos.user.event.LawyerDeletedSpringEvent;
import com.pravoos.user.exception.LawyerNotFoundException;
import com.pravoos.user.model.dto.ClientStatsResponse;
import com.pravoos.user.model.dto.LawyerProfileResponse;
import com.pravoos.user.model.entity.LawyerProfile;
import com.pravoos.user.model.entity.User;
import com.pravoos.user.model.enums.UserRole;
import com.pravoos.user.model.enums.UserStatus;
import com.pravoos.user.repository.LawyerApplicationRepository;
import com.pravoos.user.repository.UserRepository;
import com.pravoos.user.util.EmailMasker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class AdminService {

    private static final Logger log = LoggerFactory.getLogger(AdminService.class);

    private final UserRepository userRepository;
    private final LawyerApplicationRepository lawyerApplicationRepository;
    private final ApplicationEventPublisher eventPublisher;

    public AdminService(UserRepository userRepository,
                        LawyerApplicationRepository lawyerApplicationRepository,
                        ApplicationEventPublisher eventPublisher) {
        this.userRepository = userRepository;
        this.lawyerApplicationRepository = lawyerApplicationRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public List<LawyerProfileResponse> getActiveLawyers() {
        return userRepository.findByRoleAndStatusWithProfile(UserRole.LAWYER, UserStatus.ACTIVE)
                .stream()
                .map(this::toLawyerProfileResponse)
                .toList();
    }

    @Transactional
    public void deleteLawyer(UUID userId, UUID adminId) {
        User lawyer = userRepository.findById(userId)
                .filter(user -> user.getRole() == UserRole.LAWYER)
                .orElseThrow(LawyerNotFoundException::new);

        String email = lawyer.getEmail();

        userRepository.delete(lawyer);
        userRepository.flush();
        lawyerApplicationRepository.deleteByEmail(email);

        eventPublisher.publishEvent(new LawyerDeletedSpringEvent(userId));

        log.warn("Lawyer {} ({}) deleted by admin {}", userId, EmailMasker.mask(email), adminId);
    }

    @Transactional(readOnly = true)
    public ClientStatsResponse getClientStats() {
        LocalDateTime weekAgo = LocalDateTime.now().minusDays(7);
        long totalActive = userRepository.countByRoleAndStatus(UserRole.LAWYER, UserStatus.ACTIVE);
        long newThisWeek = userRepository.countByRoleAndStatusAndCreatedAtAfter(UserRole.LAWYER, UserStatus.ACTIVE, weekAgo);
        return new ClientStatsResponse(newThisWeek, totalActive);
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
