package com.pravoos.user.identity.api;

import com.pravoos.common.web.AiProcessingMode;
import java.util.UUID;

public interface AiProcessingModeProvider {

  AiProcessingMode resolveMode(UUID userId);
}
