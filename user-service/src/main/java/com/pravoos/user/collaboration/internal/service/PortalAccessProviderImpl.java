package com.pravoos.user.collaboration.internal.service;

import com.pravoos.user.collaboration.internal.repository.ClientPortalInviteRepository;
import com.pravoos.user.identity.api.PortalAccessProvider;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class PortalAccessProviderImpl implements PortalAccessProvider {

  private final ClientPortalInviteRepository clientPortalInviteRepository;

  public PortalAccessProviderImpl(ClientPortalInviteRepository clientPortalInviteRepository) {
    this.clientPortalInviteRepository = clientPortalInviteRepository;
  }

  @Override
  public List<UUID> acceptedClientIdsForUser(UUID userId) {
    return clientPortalInviteRepository.findAcceptedClientIdsByUserId(userId);
  }
}
