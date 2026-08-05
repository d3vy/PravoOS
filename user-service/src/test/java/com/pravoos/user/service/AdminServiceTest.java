package com.pravoos.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.pravoos.user.registration.internal.service.AdminService;
import com.pravoos.user.shared.exception.LawyerNotFoundException;
import com.pravoos.user.shared.service.OutboxEventService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private LawyerApplicationRepository lawyerApplicationRepository;
  @Mock private OutboxEventService outboxEventService;
  @Mock private TokenDenylistService tokenDenylistService;
  @Mock private LawyerMembershipCleanup lawyerMembershipCleanup;

  private AdminService adminService;

  @BeforeEach
  void setUp() {
    adminService =
        new AdminService(
            userRepository,
            lawyerApplicationRepository,
            outboxEventService,
            tokenDenylistService,
            lawyerMembershipCleanup);
  }

  @Test
  void getActiveLawyers_mapsProfileFields_whenProfilePresent() {
    User lawyer = lawyer("lawyer@example.com");
    LawyerProfile profile = new LawyerProfile();
    profile.setFullName("Иван Иванов");
    profile.setSpecialization("Corporate");
    profile.setPhone("+79990000000");
    profile.setTelegramChatId(555L);
    lawyer.setLawyerProfile(profile);
    when(userRepository.findByRoleAndStatusWithProfile(
            eq(UserRole.LAWYER), eq(UserStatus.ACTIVE), any(Pageable.class)))
        .thenReturn(List.of(lawyer));

    List<LawyerProfileResponse> result = adminService.getActiveLawyers(0, 20);

    assertThat(result).hasSize(1);
    LawyerProfileResponse response = result.get(0);
    assertThat(response.userId()).isEqualTo(lawyer.getId());
    assertThat(response.email()).isEqualTo("lawyer@example.com");
    assertThat(response.fullName()).isEqualTo("Иван Иванов");
    assertThat(response.specialization()).isEqualTo("Corporate");
    assertThat(response.phone()).isEqualTo("+79990000000");
    assertThat(response.telegramLinked()).isTrue();
  }

  @Test
  void getActiveLawyers_returnsNullFields_whenProfileAbsent() {
    User lawyer = lawyer("nolawyerprofile@example.com");
    when(userRepository.findByRoleAndStatusWithProfile(
            eq(UserRole.LAWYER), eq(UserStatus.ACTIVE), any(Pageable.class)))
        .thenReturn(List.of(lawyer));

    List<LawyerProfileResponse> result = adminService.getActiveLawyers(0, 20);

    LawyerProfileResponse response = result.get(0);
    assertThat(response.fullName()).isNull();
    assertThat(response.specialization()).isNull();
    assertThat(response.phone()).isNull();
    assertThat(response.telegramLinked()).isFalse();
  }

  @Test
  void countActiveLawyers_delegatesToRepository() {
    when(userRepository.countByRoleAndStatus(UserRole.LAWYER, UserStatus.ACTIVE)).thenReturn(7L);

    assertThat(adminService.countActiveLawyers()).isEqualTo(7L);
  }

  @Test
  void deleteLawyer_throws_whenUserNotFound() {
    UUID userId = UUID.randomUUID();
    UUID adminId = UUID.randomUUID();
    when(userRepository.findById(userId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> adminService.deleteLawyer(userId, adminId))
        .isInstanceOf(LawyerNotFoundException.class);
    verify(lawyerMembershipCleanup, never()).purgeMembershipsForDeletedLawyer(any());
  }

  @Test
  void deleteLawyer_throws_whenUserIsNotLawyer() {
    UUID userId = UUID.randomUUID();
    UUID adminId = UUID.randomUUID();
    User client = new User();
    client.setId(userId);
    client.setRole(UserRole.CLIENT);
    when(userRepository.findById(userId)).thenReturn(Optional.of(client));

    assertThatThrownBy(() -> adminService.deleteLawyer(userId, adminId))
        .isInstanceOf(LawyerNotFoundException.class);
    verify(userRepository, never()).delete(any());
  }

  @Test
  void deleteLawyer_purgesMembershipsRevokesTokensAndPublishesEvent() {
    UUID userId = UUID.randomUUID();
    UUID adminId = UUID.randomUUID();
    User lawyer = lawyer("todelete@example.com");
    lawyer.setId(userId);
    when(userRepository.findById(userId)).thenReturn(Optional.of(lawyer));
    UUID orgId = UUID.randomUUID();
    UUID newOwnerId = UUID.randomUUID();
    Map<UUID, UUID> orgCaseOwners = Map.of(orgId, newOwnerId);
    when(lawyerMembershipCleanup.purgeMembershipsForDeletedLawyer(userId))
        .thenReturn(orgCaseOwners);

    adminService.deleteLawyer(userId, adminId);

    verify(lawyerMembershipCleanup).purgeMembershipsForDeletedLawyer(userId);
    verify(userRepository).delete(lawyer);
    verify(userRepository).flush();
    verify(lawyerApplicationRepository).deleteByEmail("todelete@example.com");
    verify(tokenDenylistService).revokeAccessTokensFor(userId);

    ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
    verify(outboxEventService).enqueue(eq("lawyer.deleted"), eq(userId.toString()), payloadCaptor.capture());
    LawyerDeletedKafkaPayload payload = (LawyerDeletedKafkaPayload) payloadCaptor.getValue();
    assertThat(payload.userId()).isEqualTo(userId);
    assertThat(payload.orgCaseOwners()).isEqualTo(orgCaseOwners);
  }

  @Test
  void getClientStats_returnsCountsFromRepository() {
    when(userRepository.countByRoleAndStatus(UserRole.LAWYER, UserStatus.ACTIVE)).thenReturn(50L);
    when(userRepository.countByRoleAndStatusAndCreatedAtAfter(
            eq(UserRole.LAWYER), eq(UserStatus.ACTIVE), any()))
        .thenReturn(3L);

    ClientStatsResponse stats = adminService.getClientStats();

    assertThat(stats.totalActive()).isEqualTo(50L);
    assertThat(stats.newThisWeek()).isEqualTo(3L);
  }

  private User lawyer(String email) {
    User user = new User();
    user.setId(UUID.randomUUID());
    user.setEmail(email);
    user.setRole(UserRole.LAWYER);
    return user;
  }
}
