package com.pravoos.user.service;

import com.pravoos.user.event.ClientPortalInviteCreatedEvent;
import com.pravoos.user.exception.EmailAlreadyExistsException;
import com.pravoos.user.exception.InvalidInviteException;
import com.pravoos.user.exception.TooManyRequestsException;
import com.pravoos.user.model.dto.CreatePortalInviteRequest;
import com.pravoos.user.model.dto.PortalInvitePreviewResponse;
import com.pravoos.user.model.dto.PortalInviteStatusResponse;
import com.pravoos.user.model.entity.ClientPortalInvite;
import com.pravoos.user.model.entity.User;
import com.pravoos.user.model.enums.InviteStatus;
import com.pravoos.user.model.enums.UserRole;
import com.pravoos.user.model.enums.UserStatus;
import com.pravoos.user.repository.ClientPortalInviteRepository;
import com.pravoos.user.repository.UserRepository;
import com.pravoos.user.security.TokenHasher;
import com.pravoos.user.util.EmailMasker;
import com.pravoos.user.util.EmailNormalizer;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Service
public class ClientPortalInviteService {

    private static final Logger log = LoggerFactory.getLogger(ClientPortalInviteService.class);
    private static final int TOKEN_BYTE_LENGTH = 32;
    private static final int INVITE_EXPIRY_DAYS = 7;

    private final ClientPortalInviteRepository inviteRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenHasher tokenHasher;
    private final EmailRateLimiter emailRateLimiter;
    private final ApplicationEventPublisher eventPublisher;
    private final TokenDenylistService tokenDenylistService;
    private final SecureRandom secureRandom = new SecureRandom();

    public ClientPortalInviteService(ClientPortalInviteRepository inviteRepository,
                                     UserRepository userRepository,
                                     PasswordEncoder passwordEncoder,
                                     TokenHasher tokenHasher,
                                     EmailRateLimiter emailRateLimiter,
                                     ApplicationEventPublisher eventPublisher,
                                     TokenDenylistService tokenDenylistService) {
        this.inviteRepository = inviteRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenHasher = tokenHasher;
        this.emailRateLimiter = emailRateLimiter;
        this.eventPublisher = eventPublisher;
        this.tokenDenylistService = tokenDenylistService;
    }

    @Transactional
    public void createInvite(CreatePortalInviteRequest request) {
        String email = EmailNormalizer.normalize(request.email());
        if (!emailRateLimiter.allow("portal-invite", email)) {
            throw new TooManyRequestsException();
        }

        inviteRepository.revokePendingByClientId(request.clientId());

        String rawToken = generateRawToken();
        ClientPortalInvite invite = new ClientPortalInvite();
        invite.setClientId(request.clientId());
        invite.setLawyerId(request.lawyerId());
        invite.setEmail(email);
        invite.setTokenHash(tokenHasher.sha256Hex(rawToken));
        invite.setStatus(InviteStatus.PENDING);
        invite.setExpiresAt(LocalDateTime.now(ZoneOffset.UTC).plusDays(INVITE_EXPIRY_DAYS));
        inviteRepository.save(invite);

        eventPublisher.publishEvent(new ClientPortalInviteCreatedEvent(email, request.clientName(), rawToken));
        log.info("Client portal invite created for client {} ({})", request.clientId(), EmailMasker.mask(email));
    }

    @Transactional(readOnly = true)
    public PortalInviteStatusResponse status(UUID clientId) {
        if (inviteRepository.existsByClientIdAndStatus(clientId, InviteStatus.ACCEPTED)) {
            return PortalInviteStatusResponse.accepted();
        }
        return inviteRepository.findFirstByClientIdAndStatusOrderByCreatedAtDesc(clientId, InviteStatus.PENDING)
                .filter(invite -> invite.getExpiresAt().isAfter(LocalDateTime.now(ZoneOffset.UTC)))
                .map(invite -> PortalInviteStatusResponse.pending(invite.getEmail(), invite.getExpiresAt()))
                .orElseGet(PortalInviteStatusResponse::none);
    }

    @Transactional
    public void revokeAccess(UUID clientId) {
        inviteRepository.revokePendingByClientId(clientId);

        List<UUID> affectedUserIds = inviteRepository.findAcceptedUserIdsByClientId(clientId);
        if (affectedUserIds.isEmpty()) {
            log.info("Pending portal invites revoked for client {}", clientId);
            return;
        }

        inviteRepository.revokeAcceptedByClientId(clientId);
        affectedUserIds.forEach(tokenDenylistService::revokeAccessTokensFor);
        log.info("Portal access revoked for client {} — {} user(s) affected, access tokens denylisted",
                clientId, affectedUserIds.size());
    }

    @Transactional(readOnly = true)
    public PortalInvitePreviewResponse preview(String rawToken) {
        ClientPortalInvite invite = requirePendingInvite(rawToken);
        return new PortalInvitePreviewResponse(invite.getEmail(), null);
    }

    @Transactional
    public UUID accept(String rawToken, String rawPassword) {
        ClientPortalInvite invite = requirePendingInvite(rawToken);

        if (userRepository.existsByEmail(invite.getEmail())) {
            throw new EmailAlreadyExistsException(invite.getEmail());
        }

        User user = new User();
        user.setEmail(invite.getEmail());
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setRole(UserRole.CLIENT);
        user.setStatus(UserStatus.ACTIVE);
        User saved = userRepository.save(user);

        invite.setStatus(InviteStatus.ACCEPTED);
        invite.setAcceptedAt(LocalDateTime.now(ZoneOffset.UTC));
        invite.setUserId(saved.getId());

        log.info("Client portal access created for {} (client {})",
                EmailMasker.mask(invite.getEmail()), invite.getClientId());
        return saved.getId();
    }

    private ClientPortalInvite requirePendingInvite(String rawToken) {
        ClientPortalInvite invite = inviteRepository.findByTokenHash(tokenHasher.sha256Hex(rawToken))
                .orElseThrow(InvalidInviteException::new);
        if (invite.getStatus() != InviteStatus.PENDING
                || invite.getExpiresAt().isBefore(LocalDateTime.now(ZoneOffset.UTC))) {
            throw new InvalidInviteException();
        }
        return invite;
    }

    @Scheduled(cron = "0 50 3 * * *")
    @SchedulerLock(name = "ClientPortalInviteService_purgeExpired", lockAtMostFor = "PT10M")
    @Transactional
    public void purgeExpiredInvites() {
        int deleted = inviteRepository.deleteByStatusNotAndExpiresAtBefore(
                InviteStatus.ACCEPTED, LocalDateTime.now(ZoneOffset.UTC).minusDays(INVITE_EXPIRY_DAYS));
        if (deleted > 0) {
            log.info("Purged {} expired client portal invites", deleted);
        }
    }

    private String generateRawToken() {
        byte[] randomBytes = new byte[TOKEN_BYTE_LENGTH];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }
}
