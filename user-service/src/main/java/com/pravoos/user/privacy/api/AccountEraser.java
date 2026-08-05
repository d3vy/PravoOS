package com.pravoos.user.privacy.api;

import java.util.UUID;

public interface AccountEraser {

  boolean supports(UUID userId);

  void erase(UUID userId);
}
