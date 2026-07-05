package com.pravoos.user.service;
import com.pravoos.user.collaboration.internal.service.ClientPortalInviteService;

import com.pravoos.user.identity.api.TokenDenylistService;
import com.pravoos.user.shared.service.EmailRateLimiter;
import com.pravoos.user.identity.api.PasswordPolicyService;
import com.pravoos.user.collaboration.internal.event.ClientPortalInviteCreatedEvent;
import com.pravoos.user.shared.exception.InvalidCredentialsException;
import com.pravoos.user.shared.exception.InvalidInviteException;
import com.pravoos.user.shared.exception.PortalAccountConflictException;
import com.pravoos.user.shared.exception.TooManyRequestsException;
import com.pravoos.user.collaboration.internal.dto.CreatePortalInviteRequest;
import com.pravoos.user.collaboration.internal.dto.PortalInvitePreviewResponse;
import com.pravoos.user.collaboration.internal.dto.PortalInviteStatusResponse;
import com.pravoos.user.collaboration.internal.model.entity.ClientPortalInvite;
import com.pravoos.user.identity.model.entity.User;
import com.pravoos.user.collaboration.internal.model.enums.InviteStatus;
import com.pravoos.user.collaboration.internal.model.enums.PortalAccessStatus;
import com.pravoos.user.identity.model.enums.UserRole;
import com.pravoos.user.identity.model.enums.UserStatus;
import com.pravoos.user.collaboration.internal.repository.ClientPortalInviteRepository;
import com.pravoos.user.identity.repository.UserRepository;
import com.pravoos.user.shared.security.TokenHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClientPortalInviteServiceTest {

    @Mock private ClientPortalInviteRepository inviteRepository;
    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private PasswordPolicyService passwordPolicyService;
    @Mock private EmailRateLimiter emailRateLimiter;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private TokenDenylistService tokenDenylistService;

    private final TokenHasher tokenHasher = new TokenHasher();
    private ClientPortalInviteService service;

    @BeforeEach
    void setUp() {
        service = new ClientPortalInviteService(inviteRepository, userRepository, passwordEncoder,
                passwordPolicyService, tokenHasher, emailRateLimiter, eventPublisher, tokenDenylistService);
    }

    @Test
    void createInviteRevokesPendingAndPublishesEvent() {
        UUID clientId = UUID.randomUUID();
        UUID lawyerId = UUID.randomUUID();
        when(emailRateLimiter.allow("portal-invite", "client@example.com")).thenReturn(true);

        service.createInvite(new CreatePortalInviteRequest(clientId, lawyerId, "Client@Example.com", "ООО Ромашка"));

        verify(inviteRepository).revokePendingByClientId(clientId);
        ArgumentCaptor<ClientPortalInvite> inviteCaptor = ArgumentCaptor.forClass(ClientPortalInvite.class);
        verify(inviteRepository).save(inviteCaptor.capture());
        ClientPortalInvite saved = inviteCaptor.getValue();
        assertThat(saved.getClientId()).isEqualTo(clientId);
        assertThat(saved.getLawyerId()).isEqualTo(lawyerId);
        assertThat(saved.getEmail()).isEqualTo("client@example.com");
        assertThat(saved.getStatus()).isEqualTo(InviteStatus.PENDING);
        assertThat(saved.getTokenHash()).isNotBlank();

        ArgumentCaptor<ClientPortalInviteCreatedEvent> eventCaptor =
                ArgumentCaptor.forClass(ClientPortalInviteCreatedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().email()).isEqualTo("client@example.com");
        assertThat(eventCaptor.getValue().clientName()).isEqualTo("ООО Ромашка");
        assertThat(eventCaptor.getValue().rawToken()).isNotBlank();
    }

    @Test
    void createInviteRejectedWhenRateLimited() {
        when(emailRateLimiter.allow("portal-invite", "client@example.com")).thenReturn(false);

        assertThatThrownBy(() -> service.createInvite(
                new CreatePortalInviteRequest(UUID.randomUUID(), UUID.randomUUID(), "client@example.com", "Клиент")))
                .isInstanceOf(TooManyRequestsException.class);

        verify(inviteRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void acceptCreatesClientUserOnValidToken() {
        String rawToken = "raw-token";
        UUID clientId = UUID.randomUUID();
        UUID generatedUserId = UUID.randomUUID();
        ClientPortalInvite invite = pendingInvite(clientId, "client@example.com", rawToken);
        when(inviteRepository.findByTokenHash(tokenHasher.sha256Hex(rawToken))).thenReturn(Optional.of(invite));
        when(userRepository.findByEmail("client@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("Passw0rd!")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(generatedUserId);
            return user;
        });

        UUID result = service.accept(rawToken, "Passw0rd!");

        assertThat(result).isEqualTo(generatedUserId);
        assertThat(invite.getStatus()).isEqualTo(InviteStatus.ACCEPTED);
        assertThat(invite.getUserId()).isEqualTo(generatedUserId);
        assertThat(invite.getAcceptedAt()).isNotNull();

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User created = userCaptor.getValue();
        assertThat(created.getRole()).isEqualTo(UserRole.CLIENT);
        assertThat(created.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(created.getPasswordHash()).isEqualTo("hashed");
        assertThat(created.getEmail()).isEqualTo("client@example.com");
    }

    @Test
    void acceptRejectsInvalidToken() {
        when(inviteRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.accept("missing", "Passw0rd!"))
                .isInstanceOf(InvalidInviteException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void acceptRejectsExpiredInvite() {
        String rawToken = "raw-token";
        ClientPortalInvite invite = pendingInvite(UUID.randomUUID(), "client@example.com", rawToken);
        invite.setExpiresAt(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1));
        when(inviteRepository.findByTokenHash(tokenHasher.sha256Hex(rawToken))).thenReturn(Optional.of(invite));

        assertThatThrownBy(() -> service.accept(rawToken, "Passw0rd!"))
                .isInstanceOf(InvalidInviteException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void acceptRejectsAlreadyAcceptedInvite() {
        String rawToken = "raw-token";
        ClientPortalInvite invite = pendingInvite(UUID.randomUUID(), "client@example.com", rawToken);
        invite.setStatus(InviteStatus.ACCEPTED);
        when(inviteRepository.findByTokenHash(tokenHasher.sha256Hex(rawToken))).thenReturn(Optional.of(invite));

        assertThatThrownBy(() -> service.accept(rawToken, "Passw0rd!"))
                .isInstanceOf(InvalidInviteException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void acceptLinksExistingClientAccountWhenPasswordMatches() {
        String rawToken = "raw-token";
        UUID clientId = UUID.randomUUID();
        UUID existingUserId = UUID.randomUUID();
        ClientPortalInvite invite = pendingInvite(clientId, "client@example.com", rawToken);
        User existing = clientUser(existingUserId, "existing-hash", UserStatus.ACTIVE);
        when(inviteRepository.findByTokenHash(tokenHasher.sha256Hex(rawToken))).thenReturn(Optional.of(invite));
        when(userRepository.findByEmail("client@example.com")).thenReturn(Optional.of(existing));
        when(passwordEncoder.matches("Passw0rd!", "existing-hash")).thenReturn(true);

        UUID result = service.accept(rawToken, "Passw0rd!");

        assertThat(result).isEqualTo(existingUserId);
        assertThat(invite.getStatus()).isEqualTo(InviteStatus.ACCEPTED);
        assertThat(invite.getUserId()).isEqualTo(existingUserId);
        verify(userRepository, never()).save(any());
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void acceptRejectsExistingClientAccountWhenPasswordWrong() {
        String rawToken = "raw-token";
        ClientPortalInvite invite = pendingInvite(UUID.randomUUID(), "client@example.com", rawToken);
        User existing = clientUser(UUID.randomUUID(), "existing-hash", UserStatus.ACTIVE);
        when(inviteRepository.findByTokenHash(tokenHasher.sha256Hex(rawToken))).thenReturn(Optional.of(invite));
        when(userRepository.findByEmail("client@example.com")).thenReturn(Optional.of(existing));
        when(passwordEncoder.matches("wrong", "existing-hash")).thenReturn(false);

        assertThatThrownBy(() -> service.accept(rawToken, "wrong"))
                .isInstanceOf(InvalidCredentialsException.class);
        assertThat(invite.getStatus()).isEqualTo(InviteStatus.PENDING);
        verify(userRepository, never()).save(any());
    }

    @Test
    void acceptRejectsWhenExistingAccountIsNotClient() {
        String rawToken = "raw-token";
        ClientPortalInvite invite = pendingInvite(UUID.randomUUID(), "client@example.com", rawToken);
        User lawyer = clientUser(UUID.randomUUID(), "existing-hash", UserStatus.ACTIVE);
        lawyer.setRole(UserRole.LAWYER);
        when(inviteRepository.findByTokenHash(tokenHasher.sha256Hex(rawToken))).thenReturn(Optional.of(invite));
        when(userRepository.findByEmail("client@example.com")).thenReturn(Optional.of(lawyer));

        assertThatThrownBy(() -> service.accept(rawToken, "Passw0rd!"))
                .isInstanceOf(PortalAccountConflictException.class);
        verify(passwordEncoder, never()).matches(any(), any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void acceptRejectsWhenExistingClientAccountIsNotActive() {
        String rawToken = "raw-token";
        ClientPortalInvite invite = pendingInvite(UUID.randomUUID(), "client@example.com", rawToken);
        User disabled = clientUser(UUID.randomUUID(), "existing-hash", UserStatus.REJECTED);
        when(inviteRepository.findByTokenHash(tokenHasher.sha256Hex(rawToken))).thenReturn(Optional.of(invite));
        when(userRepository.findByEmail("client@example.com")).thenReturn(Optional.of(disabled));

        assertThatThrownBy(() -> service.accept(rawToken, "Passw0rd!"))
                .isInstanceOf(PortalAccountConflictException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void previewReturnsInviteEmailAndAccountExistsFlag() {
        String rawToken = "raw-token";
        ClientPortalInvite invite = pendingInvite(UUID.randomUUID(), "client@example.com", rawToken);
        when(inviteRepository.findByTokenHash(tokenHasher.sha256Hex(rawToken))).thenReturn(Optional.of(invite));
        when(userRepository.existsByEmail("client@example.com")).thenReturn(true);

        PortalInvitePreviewResponse preview = service.preview(rawToken);

        assertThat(preview.email()).isEqualTo("client@example.com");
        assertThat(preview.accountExists()).isTrue();
    }

    @Test
    void statusReturnsAcceptedWhenAccessExists() {
        UUID clientId = UUID.randomUUID();
        when(inviteRepository.existsByClientIdAndStatus(clientId, InviteStatus.ACCEPTED)).thenReturn(true);

        PortalInviteStatusResponse status = service.status(clientId);

        assertThat(status.status()).isEqualTo(PortalAccessStatus.ACCEPTED);
        verify(inviteRepository, never()).findFirstByClientIdAndStatusOrderByCreatedAtDesc(any(), any());
    }

    @Test
    void statusReturnsPendingWithEmailAndExpiry() {
        UUID clientId = UUID.randomUUID();
        ClientPortalInvite invite = pendingInvite(clientId, "client@example.com", "raw-token");
        when(inviteRepository.existsByClientIdAndStatus(clientId, InviteStatus.ACCEPTED)).thenReturn(false);
        when(inviteRepository.findFirstByClientIdAndStatusOrderByCreatedAtDesc(clientId, InviteStatus.PENDING))
                .thenReturn(Optional.of(invite));

        PortalInviteStatusResponse status = service.status(clientId);

        assertThat(status.status()).isEqualTo(PortalAccessStatus.PENDING);
        assertThat(status.email()).isEqualTo("client@example.com");
        assertThat(status.expiresAt()).isEqualTo(invite.getExpiresAt());
    }

    @Test
    void statusReturnsNoneWhenPendingExpired() {
        UUID clientId = UUID.randomUUID();
        ClientPortalInvite invite = pendingInvite(clientId, "client@example.com", "raw-token");
        invite.setExpiresAt(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1));
        when(inviteRepository.existsByClientIdAndStatus(clientId, InviteStatus.ACCEPTED)).thenReturn(false);
        when(inviteRepository.findFirstByClientIdAndStatusOrderByCreatedAtDesc(clientId, InviteStatus.PENDING))
                .thenReturn(Optional.of(invite));

        PortalInviteStatusResponse status = service.status(clientId);

        assertThat(status.status()).isEqualTo(PortalAccessStatus.NONE);
        assertThat(status.email()).isNull();
    }

    @Test
    void statusReturnsNoneWhenNoInvite() {
        UUID clientId = UUID.randomUUID();
        when(inviteRepository.existsByClientIdAndStatus(clientId, InviteStatus.ACCEPTED)).thenReturn(false);
        when(inviteRepository.findFirstByClientIdAndStatusOrderByCreatedAtDesc(clientId, InviteStatus.PENDING))
                .thenReturn(Optional.empty());

        assertThat(service.status(clientId).status()).isEqualTo(PortalAccessStatus.NONE);
    }

    @Test
    void revokeAccessRevokesPendingOnly_whenNoAcceptedAccess() {
        UUID clientId = UUID.randomUUID();
        when(inviteRepository.findAcceptedUserIdsByClientId(clientId)).thenReturn(List.of());

        service.revokeAccess(clientId);

        verify(inviteRepository).revokePendingByClientId(clientId);
        verify(inviteRepository, never()).revokeAcceptedByClientId(any());
        verify(tokenDenylistService, never()).revokeAccessTokensFor(any());
    }

    @Test
    void revokeAccessRevokesAcceptedAndDenylistsAffectedUsers() {
        UUID clientId = UUID.randomUUID();
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        when(inviteRepository.findAcceptedUserIdsByClientId(clientId)).thenReturn(List.of(userA, userB));

        service.revokeAccess(clientId);

        verify(inviteRepository).revokePendingByClientId(clientId);
        verify(inviteRepository).revokeAcceptedByClientId(clientId);
        verify(tokenDenylistService).revokeAccessTokensFor(userA);
        verify(tokenDenylistService).revokeAccessTokensFor(userB);
    }

    private ClientPortalInvite pendingInvite(UUID clientId, String email, String rawToken) {
        ClientPortalInvite invite = new ClientPortalInvite();
        invite.setClientId(clientId);
        invite.setLawyerId(UUID.randomUUID());
        invite.setEmail(email);
        invite.setTokenHash(tokenHasher.sha256Hex(rawToken));
        invite.setStatus(InviteStatus.PENDING);
        invite.setExpiresAt(LocalDateTime.now(ZoneOffset.UTC).plusDays(7));
        return invite;
    }

    private User clientUser(UUID id, String passwordHash, UserStatus status) {
        User user = new User();
        user.setId(id);
        user.setEmail("client@example.com");
        user.setPasswordHash(passwordHash);
        user.setRole(UserRole.CLIENT);
        user.setStatus(status);
        return user;
    }
}
