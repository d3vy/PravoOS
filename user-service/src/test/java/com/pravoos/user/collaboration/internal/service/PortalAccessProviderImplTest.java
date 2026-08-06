package com.pravoos.user.collaboration.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.pravoos.user.collaboration.internal.repository.ClientPortalInviteRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PortalAccessProviderImplTest {

  @Mock private ClientPortalInviteRepository clientPortalInviteRepository;

  private PortalAccessProviderImpl provider;

  @BeforeEach
  void setUp() {
    provider = new PortalAccessProviderImpl(clientPortalInviteRepository);
  }

  @Test
  void acceptedClientIdsForUser_delegatesToRepository() {
    UUID userId = UUID.randomUUID();
    UUID clientId = UUID.randomUUID();
    when(clientPortalInviteRepository.findAcceptedClientIdsByUserId(userId))
        .thenReturn(List.of(clientId));

    List<UUID> result = provider.acceptedClientIdsForUser(userId);

    assertThat(result).containsExactly(clientId);
  }

  @Test
  void acceptedClientIdsForUser_returnsEmptyListWhenNoAcceptedInvites() {
    UUID userId = UUID.randomUUID();
    when(clientPortalInviteRepository.findAcceptedClientIdsByUserId(userId)).thenReturn(List.of());

    List<UUID> result = provider.acceptedClientIdsForUser(userId);

    assertThat(result).isEmpty();
  }
}
