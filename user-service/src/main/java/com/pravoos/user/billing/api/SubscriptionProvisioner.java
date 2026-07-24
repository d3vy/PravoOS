package com.pravoos.user.billing.api;

import java.util.UUID;

public interface SubscriptionProvisioner {

  void startTrial(UUID userId);
}
