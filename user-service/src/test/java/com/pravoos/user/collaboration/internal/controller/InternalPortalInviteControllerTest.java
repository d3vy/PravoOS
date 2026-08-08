package com.pravoos.user.collaboration.internal.controller;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pravoos.user.collaboration.internal.dto.CreatePortalInviteRequest;
import com.pravoos.user.collaboration.internal.dto.PortalInviteStatusResponse;
import com.pravoos.user.collaboration.internal.model.enums.PortalAccessStatus;
import com.pravoos.user.collaboration.internal.service.ClientPortalInviteService;
import com.pravoos.user.shared.exception.GlobalExceptionHandler;
import com.pravoos.user.shared.exception.TooManyRequestsException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class InternalPortalInviteControllerTest {

  @Mock private ClientPortalInviteService clientPortalInviteService;

  private MockMvc mockMvc;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @BeforeEach
  void setUp() {
    InternalPortalInviteController controller =
        new InternalPortalInviteController(clientPortalInviteService);
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  void createReturns202OnSuccess() throws Exception {
    CreatePortalInviteRequest request =
        new CreatePortalInviteRequest(
            UUID.randomUUID(), UUID.randomUUID(), "client@example.com", "Client Name");
    doNothing().when(clientPortalInviteService).createInvite(request);

    mockMvc
        .perform(
            post("/internal/portal-invites")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isAccepted());

    verify(clientPortalInviteService).createInvite(request);
  }

  @Test
  void createReturns429WhenRateLimited() throws Exception {
    CreatePortalInviteRequest request =
        new CreatePortalInviteRequest(
            UUID.randomUUID(), UUID.randomUUID(), "client@example.com", null);
    doThrow(new TooManyRequestsException()).when(clientPortalInviteService).createInvite(request);

    mockMvc
        .perform(
            post("/internal/portal-invites")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isTooManyRequests());
  }

  @Test
  void createReturns400ForInvalidEmail() throws Exception {
    String invalidJson =
        objectMapper.writeValueAsString(
            new CreatePortalInviteRequest(
                UUID.randomUUID(), UUID.randomUUID(), "not-an-email", null));

    mockMvc
        .perform(
            post("/internal/portal-invites")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidJson))
        .andExpect(status().isBadRequest());
  }

  @Test
  void statusReturnsNoneWhenNoInvite() throws Exception {
    UUID clientId = UUID.randomUUID();
    when(clientPortalInviteService.status(clientId)).thenReturn(PortalInviteStatusResponse.none());

    mockMvc
        .perform(get("/internal/portal-invites/status").param("clientId", clientId.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value(PortalAccessStatus.NONE.toString()));
  }

  @Test
  void statusReturnsPendingWithEmailAndExpiry() throws Exception {
    UUID clientId = UUID.randomUUID();
    LocalDateTime expiresAt = LocalDateTime.now(ZoneOffset.UTC).plusDays(7);
    when(clientPortalInviteService.status(clientId))
        .thenReturn(PortalInviteStatusResponse.pending("client@example.com", expiresAt));

    mockMvc
        .perform(get("/internal/portal-invites/status").param("clientId", clientId.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value(PortalAccessStatus.PENDING.toString()))
        .andExpect(jsonPath("$.email").value("client@example.com"));
  }

  @Test
  void statusReturnsAcceptedWithoutEmail() throws Exception {
    UUID clientId = UUID.randomUUID();
    when(clientPortalInviteService.status(clientId))
        .thenReturn(PortalInviteStatusResponse.accepted());

    mockMvc
        .perform(get("/internal/portal-invites/status").param("clientId", clientId.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value(PortalAccessStatus.ACCEPTED.toString()))
        .andExpect(jsonPath("$.email").doesNotExist());
  }

  @Test
  void revokeReturns204OnSuccess() throws Exception {
    UUID clientId = UUID.randomUUID();
    doNothing().when(clientPortalInviteService).revokeAccess(clientId);

    mockMvc
        .perform(delete("/internal/portal-invites").param("clientId", clientId.toString()))
        .andExpect(status().isNoContent());

    verify(clientPortalInviteService).revokeAccess(clientId);
  }
}
