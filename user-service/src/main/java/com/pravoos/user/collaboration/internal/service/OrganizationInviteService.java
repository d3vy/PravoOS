package com.pravoos.user.collaboration.internal.service;

import com.pravoos.user.shared.service.EmailRateLimiter;
import com.pravoos.user.collaboration.internal.event.OrganizationInviteCreatedEvent;
import com.pravoos.user.shared.exception.AlreadyInOrganizationException;
import com.pravoos.user.shared.exception.InvalidInviteException;
import com.pravoos.user.shared.exception.LawyerNotFoundException;
import com.pravoos.user.shared.exception.OrganizationAccessDeniedException;
import com.pravoos.user.shared.exception.OrganizationNotFoundException;
import com.pravoos.user.shared.exception.PravoosException;
import com.pravoos.user.shared.exception.TooManyRequestsException;
import com.pravoos.user.collaboration.internal.dto.CreateInviteRequest;
import com.pravoos.user.collaboration.internal.dto.InviteResponse;
import com.pravoos.user.collaboration.internal.dto.OrganizationResponse;
import com.pravoos.user.collaboration.internal.model.entity.Organization;
import com.pravoos.user.collaboration.internal.model.entity.OrganizationInvite;
import com.pravoos.user.collaboration.internal.model.entity.OrganizationMembership;
import com.pravoos.user.identity.model.entity.User;
import com.pravoos.user.collaboration.internal.model.enums.InviteStatus;
import com.pravoos.user.collaboration.internal.model.enums.OrgRole;
import com.pravoos.user.identity.model.enums.UserRole;
import com.pravoos.user.collaboration.internal.repository.OrganizationInviteRepository;
import com.pravoos.user.collaboration.internal.repository.OrganizationMembershipRepository;
import com.pravoos.user.collaboration.internal.repository.OrganizationRepository;
import com.pravoos.user.identity.repository.UserRepository;
import com.pravoos.user.shared.security.TokenHasher;
import com.pravoos.user.shared.util.EmailMasker;
import com.pravoos.user.shared.util.EmailNormalizer;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Service
public class OrganizationInviteService {

    private static final Logger log = LoggerFactory.getLogger(OrganizationInviteService.class);
    private static final int TOKEN_BYTE_LENGTH = 32;
    private static final int INVITE_EXPIRY_DAYS = 7;

    private final OrganizationInviteRepository inviteRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final OrganizationAccessGuard accessGuard;
    private final TokenHasher tokenHasher;
    private final EmailRateLimiter emailRateLimiter;
    private final ApplicationEventPublisher eventPublisher;
    private final SecureRandom secureRandom = new SecureRandom();

    public OrganizationInviteService(OrganizationInviteRepository inviteRepository,
                                     OrganizationMembershipRepository membershipRepository,
                                     OrganizationRepository organizationRepository,
                                     UserRepository userRepository,
                                     OrganizationAccessGuard accessGuard,
                                     TokenHasher tokenHasher,
                                     EmailRateLimiter emailRateLimiter,
                                     ApplicationEventPublisher eventPublisher) {
        this.inviteRepository = inviteRepository;
        this.membershipRepository = membershipRepository;
        this.organizationRepository = organizationRepository;
        this.userRepository = userRepository;
        this.accessGuard = accessGuard;
        this.tokenHasher = tokenHasher;
        this.emailRateLimiter = emailRateLimiter;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public InviteResponse invite(UUID callerId, UUID orgId, CreateInviteRequest request) {
        OrganizationMembership callerMembership = accessGuard.requireManagerOrOwner(orgId, callerId);
        OrgRole grantedRole = request.orgRole();
        if (grantedRole == OrgRole.OWNER) {
            throw new OrganizationAccessDeniedException();
        }
        if (grantedRole == OrgRole.MANAGER && callerMembership.getOrgRole() != OrgRole.OWNER) {
            throw new OrganizationAccessDeniedException();
        }

        String email = EmailNormalizer.normalize(request.email());
        if (!emailRateLimiter.allow("org-invite", email)) {
            throw new TooManyRequestsException();
        }

        userRepository.findByEmail(email).ifPresent(user -> {
            if (membershipRepository.existsByOrgIdAndUserId(orgId, user.getId())) {
                throw new AlreadyInOrganizationException();
            }
        });

        inviteRepository.revokePendingByOrgIdAndEmail(orgId, email);

        Organization organization = organizationRepository.findById(orgId)
                .orElseThrow(OrganizationNotFoundException::new);
        String rawToken = generateRawToken();

        OrganizationInvite invite = new OrganizationInvite();
        invite.setOrgId(orgId);
        invite.setEmail(email);
        invite.setOrgRole(grantedRole);
        invite.setTokenHash(tokenHasher.sha256Hex(rawToken));
        invite.setInvitedBy(callerId);
        invite.setStatus(InviteStatus.PENDING);
        invite.setExpiresAt(LocalDateTime.now(ZoneOffset.UTC).plusDays(INVITE_EXPIRY_DAYS));
        OrganizationInvite saved = inviteRepository.save(invite);

        eventPublisher.publishEvent(new OrganizationInviteCreatedEvent(
                email, organization.getName(), inviterName(callerId), rawToken));
        log.info("Invite to org {} created for {} by {}", orgId, EmailMasker.mask(email), callerId);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<InviteResponse> listPending(UUID callerId, UUID orgId) {
        accessGuard.requireManagerOrOwner(orgId, callerId);
        return inviteRepository.findByOrgIdAndStatusOrderByCreatedAtDesc(orgId, InviteStatus.PENDING)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void revoke(UUID callerId, UUID orgId, UUID inviteId) {
        accessGuard.requireManagerOrOwner(orgId, callerId);
        OrganizationInvite invite = inviteRepository.findById(inviteId)
                .filter(i -> i.getOrgId().equals(orgId))
                .orElseThrow(InvalidInviteException::new);
        if (invite.getStatus() != InviteStatus.PENDING) {
            throw new InvalidInviteException();
        }
        invite.setStatus(InviteStatus.REVOKED);
        log.info("Invite {} in org {} revoked by {}", inviteId, orgId, callerId);
    }

    @Transactional
    public OrganizationResponse accept(UUID userId, String rawToken) {
        OrganizationInvite invite = inviteRepository.findByTokenHash(tokenHasher.sha256Hex(rawToken))
                .orElseThrow(InvalidInviteException::new);
        if (invite.getStatus() != InviteStatus.PENDING
                || invite.getExpiresAt().isBefore(LocalDateTime.now(ZoneOffset.UTC))) {
            throw new InvalidInviteException();
        }

        User user = userRepository.findById(userId).orElseThrow(LawyerNotFoundException::new);
        if (user.getRole() != UserRole.LAWYER) {
            throw new PravoosException("Только юрист может вступить в организацию", HttpStatus.FORBIDDEN, "ORG_LAWYER_ONLY");
        }
        if (!EmailNormalizer.normalize(user.getEmail()).equals(invite.getEmail())) {
            throw new InvalidInviteException();
        }
        if (membershipRepository.existsByOrgIdAndUserId(invite.getOrgId(), userId)) {
            throw new AlreadyInOrganizationException();
        }

        OrganizationMembership membership = new OrganizationMembership();
        membership.setOrgId(invite.getOrgId());
        membership.setUserId(userId);
        membership.setOrgRole(invite.getOrgRole());
        membershipRepository.save(membership);

        invite.setStatus(InviteStatus.ACCEPTED);
        invite.setAcceptedAt(LocalDateTime.now(ZoneOffset.UTC));

        Organization organization = organizationRepository.findById(invite.getOrgId())
                .orElseThrow(OrganizationNotFoundException::new);
        long memberCount = membershipRepository.countByOrgId(organization.getId());
        log.info("User {} joined org {} as {}", userId, organization.getId(), invite.getOrgRole());
        return new OrganizationResponse(organization.getId(), organization.getName(), organization.getOwnerId(),
                invite.getOrgRole(), memberCount, organization.getCreatedAt());
    }

    @Scheduled(cron = "0 45 3 * * *")
    @SchedulerLock(name = "OrganizationInviteService_purgeExpired", lockAtMostFor = "PT10M")
    @Transactional
    public void purgeExpiredInvites() {
        int deleted = inviteRepository.deleteByExpiresAtBefore(LocalDateTime.now(ZoneOffset.UTC).minusDays(INVITE_EXPIRY_DAYS));
        if (deleted > 0) {
            log.info("Purged {} expired organization invites", deleted);
        }
    }

    private String inviterName(UUID callerId) {
        return userRepository.findById(callerId)
                .map(User::getLawyerProfile)
                .map(profile -> profile == null ? null : profile.getFullName())
                .orElse(null);
    }

    private InviteResponse toResponse(OrganizationInvite invite) {
        return new InviteResponse(invite.getId(), invite.getEmail(), invite.getOrgRole(),
                invite.getStatus(), invite.getExpiresAt(), invite.getCreatedAt());
    }

    private String generateRawToken() {
        byte[] randomBytes = new byte[TOKEN_BYTE_LENGTH];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }
}
