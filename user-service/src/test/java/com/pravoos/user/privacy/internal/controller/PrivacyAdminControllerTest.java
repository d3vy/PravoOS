package com.pravoos.user.privacy.internal.controller;

import static org.mockito.Mockito.verify;

import com.pravoos.user.privacy.internal.service.PersonalDataService;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PrivacyAdminControllerTest {

  @Mock private PersonalDataService personalDataService;

  private PrivacyAdminController controller;

  @BeforeEach
  void setUp() {
    controller = new PrivacyAdminController(personalDataService);
  }

  @Test
  void completeExtractsNoteFromRequestBody() {
    UUID requestId = UUID.randomUUID();

    controller.complete(requestId, Map.of("note", "Обработано вручную оператором"));

    verify(personalDataService).completeRequest(requestId, "Обработано вручную оператором");
  }

  @Test
  void completePassesNullNoteWhenBodyIsAbsent() {
    UUID requestId = UUID.randomUUID();

    controller.complete(requestId, null);

    verify(personalDataService).completeRequest(requestId, null);
  }

  @Test
  void completePassesNullNoteWhenNoteKeyMissing() {
    UUID requestId = UUID.randomUUID();

    controller.complete(requestId, Map.of());

    verify(personalDataService).completeRequest(requestId, null);
  }

  @Test
  void pendingDelegatesToPersonalDataService() {
    controller.pending();

    verify(personalDataService).pendingRequests();
  }
}
