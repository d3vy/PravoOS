package com.pravoos.user.registration.internal.service;

import com.pravoos.common.util.PhoneNormalizer;
import com.pravoos.user.identity.model.entity.LawyerProfile;
import com.pravoos.user.identity.model.entity.User;
import com.pravoos.user.identity.model.enums.UserRole;
import com.pravoos.user.identity.model.enums.UserStatus;
import com.pravoos.user.billing.api.SubscriptionProvisioner;
import com.pravoos.user.identity.repository.UserRepository;
import com.pravoos.user.registration.internal.dto.ApplicationResponse;
import com.pravoos.user.registration.internal.dto.ApplicationSubmissionResponse;
import com.pravoos.user.registration.internal.dto.ApplyRequest;
import com.pravoos.user.registration.internal.dto.UpdateApplicationRequest;
import com.pravoos.user.registration.internal.event.ApplicationApprovedSpringEvent;
import com.pravoos.user.registration.internal.event.ApplicationSubmittedKafkaPayload;
import com.pravoos.user.registration.internal.event.ApplicationSubmittedSpringEvent;
import com.pravoos.user.registration.internal.event.VerificationEmailRequestedEvent;
import com.pravoos.user.registration.internal.model.entity.LawyerApplication;
import com.pravoos.user.registration.internal.model.enums.ApplicationStatus;
import com.pravoos.user.registration.internal.repository.LawyerApplicationRepository;
import com.pravoos.user.shared.exception.*;
import com.pravoos.user.shared.security.TokenHasher;
import com.pravoos.user.shared.service.OutboxEventService;
import com.pravoos.user.shared.util.EmailMasker;
import com.pravoos.user.shared.util.EmailNormalizer;
import com.pravoos.user.shared.util.PaginationSupport;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class ApplicationService {

    private static final Logger log = LoggerFactory.getLogger(ApplicationService.class);
    private static final Duration STATUS_TOKEN_TTL = Duration.ofDays(30);

    private final LawyerApplicationRepository applicationRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;
    private final EmailVerificationService emailVerificationService;
    private final OutboxEventService outboxEventService;
    private final TokenHasher tokenHasher;
    private final SubscriptionProvisioner subscriptionProvisioner;
    private final Counter applicationSubmittedCounter;
    private final Counter applicationApprovedCounter;
    private final Counter applicationRejectedCounter;

    public ApplicationService(LawyerApplicationRepository applicationRepository,
                               UserRepository userRepository,
                               PasswordEncoder passwordEncoder,
                               ApplicationEventPublisher eventPublisher,
                               EmailVerificationService emailVerificationService,
                               OutboxEventService outboxEventService,
                               TokenHasher tokenHasher,
                               SubscriptionProvisioner subscriptionProvisioner,
                               MeterRegistry meterRegistry) {
        this.applicationRepository = applicationRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.eventPublisher = eventPublisher;
        this.emailVerificationService = emailVerificationService;
        this.outboxEventService = outboxEventService;
        this.tokenHasher = tokenHasher;
        this.subscriptionProvisioner = subscriptionProvisioner;
        this.applicationSubmittedCounter = Counter.builder("pravoos.application").tag("action", "submitted").register(meterRegistry);
        this.applicationApprovedCounter = Counter.builder("pravoos.application").tag("action", "approved").register(meterRegistry);
        this.applicationRejectedCounter = Counter.builder("pravoos.application").tag("action", "rejected").register(meterRegistry);
    }

    @Transactional
    public ApplicationSubmissionResponse submitApplication(ApplyRequest request) {
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
        application.setSpecialization(request.specialization());
        application.setPhone(PhoneNormalizer.normalize(request.phone()));
        String rawVerificationToken = emailVerificationService.generateToken();
        application.setStatusToken(emailVerificationService.generateToken());
        application.setStatusTokenExpiresAt(LocalDateTime.now().plus(STATUS_TOKEN_TTL));
        application.setEmailVerificationToken(tokenHasher.sha256Hex(rawVerificationToken));
        application.setEmailVerificationExpiresAt(emailVerificationService.tokenExpiry());

        LawyerApplication saved = applicationRepository.save(application);
        eventPublisher.publishEvent(new ApplicationSubmittedSpringEvent(saved.getEmail(), rawVerificationToken));
        outboxEventService.enqueue("application.submitted", saved.getId().toString(),
                new ApplicationSubmittedKafkaPayload(saved.getId()));
        applicationSubmittedCounter.increment();

        log.info("Lawyer application submitted: {}", EmailMasker.mask(email));
        return new ApplicationSubmissionResponse(toApplicationResponse(saved), saved.getStatusToken());
    }

    @Transactional(readOnly = true)
    public ApplicationResponse getApplicationById(UUID applicationId) {
        return toApplicationResponse(applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ApplicationNotFoundException(applicationId)));
    }

    @Transactional(readOnly = true)
    public ApplicationResponse getApplicationByStatusToken(String statusToken) {
        return toApplicationResponse(findByValidStatusToken(statusToken));
    }

    private LawyerApplication findByValidStatusToken(String statusToken) {
        LawyerApplication application = applicationRepository.findByStatusToken(statusToken)
                .orElseThrow(ApplicationTokenNotFoundException::new);

        LocalDateTime expiresAt = application.getStatusTokenExpiresAt();
        if (expiresAt != null && expiresAt.isBefore(LocalDateTime.now())) {
            throw new ApplicationTokenNotFoundException();
        }

        return application;
    }

    @Transactional
    public ApplicationResponse updateApplication(String statusToken, UpdateApplicationRequest request) {
        LawyerApplication application = findByValidStatusToken(statusToken);

        if (application.getStatus() != ApplicationStatus.PENDING) {
            throw new ApplicationStatusException(application.getId(), application.getStatus());
        }

        String newEmail = EmailNormalizer.normalize(request.email());
        boolean emailChanged = !newEmail.equals(application.getEmail());
        if (emailChanged) {
            if (applicationRepository.existsByEmailAndStatus(newEmail, ApplicationStatus.PENDING)) {
                throw new ApplicationAlreadyExistsException(newEmail);
            }
            if (userRepository.existsByEmail(newEmail)) {
                throw new EmailAlreadyExistsException(newEmail);
            }
            application.setEmail(newEmail);
        }

        application.setFullName(request.fullName());
        application.setSpecialization(request.specialization());
        application.setPhone(PhoneNormalizer.normalize(request.phone()));
        if (request.password() != null && !request.password().isBlank()) {
            application.setPasswordHash(passwordEncoder.encode(request.password()));
        }

        if (emailChanged) {
            String rawVerificationToken = emailVerificationService.generateToken();
            application.setEmailVerified(false);
            application.setEmailVerificationToken(tokenHasher.sha256Hex(rawVerificationToken));
            application.setEmailVerificationExpiresAt(emailVerificationService.tokenExpiry());
            applicationRepository.save(application);
            eventPublisher.publishEvent(new VerificationEmailRequestedEvent(newEmail, rawVerificationToken));
            log.info("Application email changed, re-verification sent: {}", EmailMasker.mask(newEmail));
        } else {
            applicationRepository.save(application);
        }

        return toApplicationResponse(application);
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
        subscriptionProvisioner.startTrial(user.getId());

        markReviewed(application, ApplicationStatus.APPROVED, adminId);
        eventPublisher.publishEvent(new ApplicationApprovedSpringEvent(application.getEmail(), application.getFullName()));
        applicationApprovedCounter.increment();

        log.info("Application approved: {} by admin: {}", applicationId, adminId);
        return toApplicationResponse(application);
    }

    @Transactional
    public ApplicationResponse rejectApplication(UUID applicationId, UUID adminId) {
        LawyerApplication application = findPendingApplicationOrThrow(applicationId);

        markReviewed(application, ApplicationStatus.REJECTED, adminId);
        applicationRejectedCounter.increment();

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
    public List<ApplicationResponse> getPendingApplications(int page, int size) {
        return applicationRepository.findByStatusOrderBySubmittedAtDesc(ApplicationStatus.PENDING, PaginationSupport.of(page, size))
                .stream()
                .map(this::toApplicationResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ApplicationResponse> getAllApplications(int page, int size) {
        return applicationRepository.findAllByOrderBySubmittedAtDesc(PaginationSupport.of(page, size))
                .stream()
                .map(this::toApplicationResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public long countAllApplications() {
        return applicationRepository.count();
    }

    @Transactional(readOnly = true)
    public long countPendingApplications() {
        return applicationRepository.countByStatus(ApplicationStatus.PENDING);
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
                application.getSpecialization(),
                application.getPhone(),
                application.getStatus(),
                application.getSubmittedAt(),
                application.getReviewedAt(),
                application.isEmailVerified()
        );
    }
}
