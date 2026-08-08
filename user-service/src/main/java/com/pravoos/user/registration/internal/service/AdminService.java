package com.pravoos.user.registration.internal.service;

import com.pravoos.user.collaboration.api.LawyerMembershipCleanup;
import com.pravoos.user.identity.api.LawyerProfileResponse;
import com.pravoos.user.identity.api.TokenDenylistService;
import com.pravoos.user.identity.model.entity.LawyerProfile;
import com.pravoos.user.identity.model.entity.User;
import com.pravoos.user.identity.model.enums.UserRole;
import com.pravoos.user.identity.model.enums.UserStatus;
import com.pravoos.user.identity.repository.UserRepository;
import com.pravoos.user.registration.internal.dto.ClientStatsResponse;
import com.pravoos.user.registration.internal.event.LawyerDeletedKafkaPayload;
import com.pravoos.user.registration.internal.repository.LawyerApplicationRepository;
import com.pravoos.user.shared.exception.LawyerNotFoundException;
import com.pravoos.user.shared.service.OutboxEventService;
import com.pravoos.user.shared.util.EmailMasker;
import com.pravoos.user.shared.util.PaginationSupport;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminService {

  private static final Logger log = LoggerFactory.getLogger(AdminService.class);

  private final UserRepository userRepository;
  private final LawyerApplicationRepository lawyerApplicationRepository;
  private final OutboxEventService outboxEventService;
  private final TokenDenylistService tokenDenylistService;
  private final LawyerMembershipCleanup lawyerMembershipCleanup;

  public AdminService(
      UserRepository userRepository,
      LawyerApplicationRepository lawyerApplicationRepository,
      OutboxEventService outboxEventService,
      TokenDenylistService tokenDenylistService,
      LawyerMembershipCleanup lawyerMembershipCleanup) {
    this.userRepository = userRepository;
    this.lawyerApplicationRepository = lawyerApplicationRepository;
    this.outboxEventService = outboxEventService;
    this.tokenDenylistService = tokenDenylistService;
    this.lawyerMembershipCleanup = lawyerMembershipCleanup;
  }

  @Transactional(readOnly = true)
  public List<LawyerProfileResponse> getActiveLawyers(int page, int size) {
    return userRepository
        .findByRoleAndStatusWithProfile(
            UserRole.LAWYER, UserStatus.ACTIVE, PaginationSupport.of(page, size))
        .stream()
        .map(this::toLawyerProfileResponse)
        .toList();
  }

  @Transactional(readOnly = true)
  public long countActiveLawyers() {
    return userRepository.countByRoleAndStatus(UserRole.LAWYER, UserStatus.ACTIVE);
  }

  @Transactional
  public void deleteLawyer(UUID userId, UUID adminId) {
    User lawyer =
        userRepository
            .findById(userId)
            .filter(user -> user.getRole() == UserRole.LAWYER)
            .orElseThrow(LawyerNotFoundException::new);

    String email = lawyer.getEmail();

    Map<UUID, UUID> orgCaseOwners =
        lawyerMembershipCleanup.purgeMembershipsForDeletedLawyer(userId);

    userRepository.delete(lawyer);
    userRepository.flush();
    lawyerApplicationRepository.deleteByEmail(email);

    tokenDenylistService.revokeAccessTokensFor(userId);
    outboxEventService.enqueue(
        "lawyer.deleted", userId.toString(), new LawyerDeletedKafkaPayload(userId, orgCaseOwners));

    log.warn(
        "Lawyer {} ({}) deleted by admin {}, {} org case owner(s) resolved",
        userId,
        EmailMasker.mask(email),
        adminId,
        orgCaseOwners.size());
  }

  @Transactional(readOnly = true)
  public ClientStatsResponse getClientStats() {
    LocalDateTime weekAgo = LocalDateTime.now(ZoneOffset.UTC).minusDays(7);
    long totalActive = userRepository.countByRoleAndStatus(UserRole.LAWYER, UserStatus.ACTIVE);
    long newThisWeek =
        userRepository.countByRoleAndStatusAndCreatedAtAfter(
            UserRole.LAWYER, UserStatus.ACTIVE, weekAgo);
    return new ClientStatsResponse(newThisWeek, totalActive);
  }

  private LawyerProfileResponse toLawyerProfileResponse(User user) {
    LawyerProfile profile = user.getLawyerProfile();
    return new LawyerProfileResponse(
        user.getId(),
        user.getEmail(),
        profile != null ? profile.getFullName() : null,
        profile != null ? profile.getSpecialization() : null,
        profile != null ? profile.getPhone() : null,
        profile != null && profile.getTelegramChatId() != null);
  }
}
