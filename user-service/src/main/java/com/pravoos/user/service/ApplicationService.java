package com.pravoos.user.service;

import com.pravoos.user.event.ApplicationSubmittedSpringEvent;
import com.pravoos.user.exception.ApplicationAlreadyExistsException;
import com.pravoos.user.exception.ApplicationNotFoundException;
import com.pravoos.user.exception.ApplicationStatusException;
import com.pravoos.user.exception.EmailAlreadyExistsException;
import com.pravoos.user.exception.EmailNotVerifiedException;
import com.pravoos.user.model.dto.ApplyRequest;
import com.pravoos.user.model.dto.ApplicationResponse;
import com.pravoos.user.model.entity.LawyerApplication;
import com.pravoos.user.model.entity.LawyerProfile;
import com.pravoos.user.model.entity.User;
import com.pravoos.user.model.enums.ApplicationStatus;
import com.pravoos.user.model.enums.UserRole;
import com.pravoos.user.model.enums.UserStatus;
import com.pravoos.user.repository.LawyerApplicationRepository;
import com.pravoos.user.repository.UserRepository;
import com.pravoos.user.service.EmailVerificationService;
import com.pravoos.user.util.EmailMasker;
import com.pravoos.user.util.EmailNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class ApplicationService {

    private static final Logger log = LoggerFactory.getLogger(ApplicationService.class);

    private final LawyerApplicationRepository applicationRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;
    private final EmailVerificationService emailVerificationService;

    public ApplicationService(LawyerApplicationRepository applicationRepository,
                               UserRepository userRepository,
                               PasswordEncoder passwordEncoder,
                               ApplicationEventPublisher eventPublisher,
                               EmailVerificationService emailVerificationService) {
        this.applicationRepository = applicationRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.eventPublisher = eventPublisher;
        this.emailVerificationService = emailVerificationService;
    }

    @Transactional
    public ApplicationResponse submitApplication(ApplyRequest request) {
        String email = EmailNormalizer.normalize(request.email());
        if (applicationRepository.existsByEmailAndStatus(email, ApplicationStatus.PENDING)) {
            throw new ApplicationAlreadyExistsException(email);
        }
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyExistsException(email);
        }

        LawyerApplication application = new LawyerApplication();
        application.setEmail(email);
        application.setFullName(request.fullName());
        application.setPasswordHash(passwordEncoder.encode(request.password()));
        application.setBarNumber(request.barNumber());
        application.setSpecialization(request.specialization());
        application.setPhone(request.phone());
        application.setEmailVerificationToken(emailVerificationService.generateToken());
        application.setEmailVerificationExpiresAt(emailVerificationService.tokenExpiry());

        LawyerApplication saved = applicationRepository.save(application);
        eventPublisher.publishEvent(new ApplicationSubmittedSpringEvent(saved));

        log.info("Lawyer application submitted: {}", EmailMasker.mask(email));
        return toApplicationResponse(saved);
    }

    @Transactional
    public ApplicationResponse approveApplication(UUID applicationId, UUID adminId) {
        return approveApplication(applicationId, adminId, false);
    }

    @Transactional
    public ApplicationResponse approveApplication(UUID applicationId, UUID adminId, boolean skipEmailVerification) {
        LawyerApplication application = findPendingApplicationOrThrow(applicationId);

        if (!application.isEmailVerified()) {
            if (!skipEmailVerification) {
                throw new EmailNotVerifiedException();
            }
            application.setEmailVerified(true);
            application.setEmailVerificationToken(null);
            application.setEmailVerificationExpiresAt(null);
            log.info("Email verification bypassed by admin for application: {}", applicationId);
        }

        if (userRepository.existsByEmail(application.getEmail())) {
            throw new EmailAlreadyExistsException(application.getEmail());
        }

        User user = buildUserFromApplication(application);
        userRepository.save(user);

        markReviewed(application, ApplicationStatus.APPROVED, adminId);

        log.info("Application approved: {} by admin: {}", applicationId, adminId);
        return toApplicationResponse(application);
    }

    @Transactional
    public ApplicationResponse rejectApplication(UUID applicationId, UUID adminId) {
        LawyerApplication application = findPendingApplicationOrThrow(applicationId);

        markReviewed(application, ApplicationStatus.REJECTED, adminId);

        log.info("Application rejected: {} by admin: {}", applicationId, adminId);
        return toApplicationResponse(application);
    }

    private void markReviewed(LawyerApplication application, ApplicationStatus status, UUID adminId) {
        application.setStatus(status);
        application.setReviewedAt(LocalDateTime.now());
        application.setReviewedBy(adminId);
        applicationRepository.save(application);
    }

    @Transactional(readOnly = true)
    public List<ApplicationResponse> getPendingApplications() {
        return applicationRepository.findByStatusOrderBySubmittedAtDesc(ApplicationStatus.PENDING)
                .stream()
                .map(this::toApplicationResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ApplicationResponse> getAllApplications() {
        return applicationRepository.findAllByOrderBySubmittedAtDesc()
                .stream()
                .map(this::toApplicationResponse)
                .toList();
    }

    private LawyerApplication findPendingApplicationOrThrow(UUID applicationId) {
        LawyerApplication application = applicationRepository.findByIdForUpdate(applicationId)
                .orElseThrow(() -> new ApplicationNotFoundException(applicationId));

        if (application.getStatus() != ApplicationStatus.PENDING) {
            throw new ApplicationStatusException(applicationId, application.getStatus());
        }

        return application;
    }

    private User buildUserFromApplication(LawyerApplication application) {
        User user = new User();
        user.setEmail(application.getEmail());
        user.setPasswordHash(application.getPasswordHash());
        user.setRole(UserRole.LAWYER);
        user.setStatus(UserStatus.ACTIVE);

        LawyerProfile profile = new LawyerProfile();
        profile.setFullName(application.getFullName());
        profile.setBarNumber(application.getBarNumber());
        profile.setSpecialization(application.getSpecialization());
        profile.setPhone(application.getPhone());
        profile.setUser(user);

        user.setLawyerProfile(profile);
        return user;
    }

    private ApplicationResponse toApplicationResponse(LawyerApplication application) {
        return new ApplicationResponse(
                application.getId(),
                application.getEmail(),
                application.getFullName(),
                application.getBarNumber(),
                application.getSpecialization(),
                application.getPhone(),
                application.getStatus(),
                application.getSubmittedAt(),
                application.getReviewedAt(),
                application.isEmailVerified()
        );
    }
}
