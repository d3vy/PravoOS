package com.pravoos.user.collaboration.internal.service;

import com.pravoos.user.collaboration.internal.dto.CreatePortalInviteRequest;
import com.pravoos.user.collaboration.internal.dto.PortalInvitePreviewResponse;
import com.pravoos.user.collaboration.internal.dto.PortalInviteStatusResponse;
import com.pravoos.user.collaboration.internal.event.ClientPortalInviteCreatedEvent;
import com.pravoos.user.collaboration.internal.model.entity.ClientPortalInvite;
import com.pravoos.user.collaboration.internal.model.enums.InviteStatus;
import com.pravoos.user.collaboration.internal.repository.ClientPortalInviteRepository;
import com.pravoos.user.identity.api.CredentialAttemptGuard;
import com.pravoos.user.identity.api.PasswordPolicyService;
import com.pravoos.user.identity.api.TokenDenylistService;
import com.pravoos.user.identity.model.entity.User;
import com.pravoos.user.identity.model.enums.UserRole;
import com.pravoos.user.identity.model.enums.UserStatus;
import com.pravoos.user.identity.repository.UserRepository;
import com.pravoos.user.shared.exception.InvalidCredentialsException;
import com.pravoos.user.shared.exception.InvalidInviteException;
import com.pravoos.user.shared.exception.PortalAccountConflictException;
import com.pravoos.user.shared.exception.TooManyRequestsException;
import com.pravoos.user.shared.security.TokenHasher;
import com.pravoos.user.shared.service.EmailRateLimiter;
import com.pravoos.user.shared.util.EmailMasker;
import com.pravoos.user.shared.util.EmailNormalizer;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClientPortalInviteService {

  private static final Logger log = LoggerFactory.getLogger(ClientPortalInviteService.class);
  private static final int TOKEN_BYTE_LENGTH = 32;
  private static final int INVITE_EXPIRY_DAYS = 7;

  private final ClientPortalInviteRepository inviteRepository;
  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final PasswordPolicyService passwordPolicyService;
  private final TokenHasher tokenHasher;
  private final EmailRateLimiter emailRateLimiter;
  private final ApplicationEventPublisher eventPublisher;
  private final TokenDenylistService tokenDenylistService;
  private final CredentialAttemptGuard credentialAttemptGuard;
  private final SecureRandom secureRandom = new SecureRandom();

  public ClientPortalInviteService(
      ClientPortalInviteRepository inviteRepository,
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      PasswordPolicyService passwordPolicyService,
      TokenHasher tokenHasher,
      EmailRateLimiter emailRateLimiter,
      ApplicationEventPublisher eventPublisher,
      TokenDenylistService tokenDenylistService,
      CredentialAttemptGuard credentialAttemptGuard) {
    this.inviteRepository = inviteRepository;
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.passwordPolicyService = passwordPolicyService;
    this.tokenHasher = tokenHasher;
    this.emailRateLimiter = emailRateLimiter;
    this.eventPublisher = eventPublisher;
    this.tokenDenylistService = tokenDenylistService;
    this.credentialAttemptGuard = credentialAttemptGuard;
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

    eventPublisher.publishEvent(
        new ClientPortalInviteCreatedEvent(email, request.clientName(), rawToken));
    log.info(
        "Client portal invite created for client {} ({})",
        request.clientId(),
        EmailMasker.mask(email));
  }

  @Transactional(readOnly = true)
  public PortalInviteStatusResponse status(UUID clientId) {
    if (inviteRepository.existsByClientIdAndStatus(clientId, InviteStatus.ACCEPTED)) {
      return PortalInviteStatusResponse.accepted();
    }
    return inviteRepository
        .findFirstByClientIdAndStatusOrderByCreatedAtDesc(clientId, InviteStatus.PENDING)
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
    log.info(
        "Portal access revoked for client {} — {} user(s) affected, access tokens denylisted",
        clientId,
        affectedUserIds.size());
  }

  @Transactional(readOnly = true)
  public PortalInvitePreviewResponse preview(String rawToken) {
    ClientPortalInvite invite = requirePendingInvite(rawToken);
    boolean accountExists = userRepository.existsByEmail(invite.getEmail());
    return new PortalInvitePreviewResponse(invite.getEmail(), null, accountExists);
  }

  @Transactional
  public UUID accept(String rawToken, String rawPassword) {
    ClientPortalInvite invite = requirePendingInvite(rawToken);

    UUID userId =
        userRepository
            .findByEmail(invite.getEmail())
            .map(existing -> linkExistingAccount(existing, rawPassword))
            .orElseGet(() -> createClientAccount(invite.getEmail(), rawPassword));

    invite.setStatus(InviteStatus.ACCEPTED);
    invite.setAcceptedAt(LocalDateTime.now(ZoneOffset.UTC));
    invite.setUserId(userId);

    log.info(
        "Client portal access granted for {} (client {})",
        EmailMasker.mask(invite.getEmail()),
        invite.getClientId());
    return userId;
  }

  private UUID createClientAccount(String email, String rawPassword) {
    passwordPolicyService.validate(rawPassword);
    User user = new User();
    user.setEmail(email);
    user.setPasswordHash(passwordEncoder.encode(rawPassword));
    user.setRole(UserRole.CLIENT);
    user.setStatus(UserStatus.ACTIVE);
    return userRepository.save(user).getId();
  }

  private UUID linkExistingAccount(User existing, String rawPassword) {
    if (existing.getRole() != UserRole.CLIENT || existing.getStatus() != UserStatus.ACTIVE) {
      throw new PortalAccountConflictException();
    }
    credentialAttemptGuard.assertNotLocked(existing.getEmail());
    if (!passwordEncoder.matches(rawPassword, existing.getPasswordHash())) {
      credentialAttemptGuard.recordFailure(existing.getEmail());
      log.warn(
          "Failed password check while linking portal invite to {}",
          EmailMasker.mask(existing.getEmail()));
      throw new InvalidCredentialsException();
    }
    credentialAttemptGuard.recordSuccess(existing.getEmail());
    return existing.getId();
  }

  private ClientPortalInvite requirePendingInvite(String rawToken) {
    ClientPortalInvite invite =
        inviteRepository
            .findByTokenHash(tokenHasher.sha256Hex(rawToken))
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
    int deleted =
        inviteRepository.deleteByStatusNotAndExpiresAtBefore(
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
