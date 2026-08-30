package com.pravoos.ai.practice.internal.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pravoos.ai.practice.internal.dto.CreateMailboxRequest;
import com.pravoos.ai.practice.internal.dto.MailHostPresetResponse;
import com.pravoos.ai.practice.internal.dto.MailSyncResult;
import com.pravoos.ai.practice.internal.dto.MailboxResponse;
import com.pravoos.ai.practice.internal.dto.MailboxTestResult;
import com.pravoos.ai.practice.internal.dto.UpdateMailboxRequest;
import com.pravoos.ai.practice.internal.service.MailSyncService;
import com.pravoos.ai.practice.internal.service.MailboxService;
import com.pravoos.ai.shared.exception.GlobalExceptionHandler;
import com.pravoos.ai.shared.exception.MailboxNotFoundException;
import com.pravoos.ai.shared.model.enums.MailboxStatus;
import com.pravoos.ai.shared.security.CallerContextArgumentResolver;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class MailboxControllerTest {

  @Mock private MailboxService mailboxService;
  @Mock private MailSyncService mailSyncService;
  @Mock private Authentication authentication;

  private MockMvc mockMvc;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final UUID userId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    MailboxController controller = new MailboxController(mailboxService, mailSyncService);
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setCustomArgumentResolvers(new CallerContextArgumentResolver())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  private MailboxResponse sampleMailbox(UUID mailboxId) {
    return new MailboxResponse(
        mailboxId,
        "lawyer@example.com",
        "imap.example.com",
        993,
        true,
        "INBOX",
        true,
        MailboxStatus.OK,
        null,
        LocalDateTime.now(ZoneOffset.UTC),
        LocalDateTime.now(ZoneOffset.UTC));
  }

  @Test
  void presetsReturnsListWithoutAuthentication() throws Exception {
    when(mailboxService.presets())
        .thenReturn(
            List.of(
                new MailHostPresetResponse(
                    "GMAIL", "Gmail", "imap.gmail.com", 993, true, List.of("gmail.com"))));

    mockMvc
        .perform(get("/api/ai/mailboxes/presets"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].id").value("GMAIL"));
  }

  @Test
  void listReturnsMailboxesForCurrentUser() throws Exception {
    when(authentication.getPrincipal()).thenReturn(userId.toString());
    when(mailboxService.findByUser(userId)).thenReturn(List.of(sampleMailbox(UUID.randomUUID())));

    mockMvc
        .perform(get("/api/ai/mailboxes").principal(authentication))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)));
  }

  @Test
  void getReturnsMailbox() throws Exception {
    when(authentication.getPrincipal()).thenReturn(userId.toString());
    UUID mailboxId = UUID.randomUUID();
    when(mailboxService.get(mailboxId, userId)).thenReturn(sampleMailbox(mailboxId));

    mockMvc
        .perform(get("/api/ai/mailboxes/{mailboxId}", mailboxId).principal(authentication))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.emailAddress").value("lawyer@example.com"));
  }

  @Test
  void getReturns404WhenMailboxNotFound() throws Exception {
    when(authentication.getPrincipal()).thenReturn(userId.toString());
    UUID mailboxId = UUID.randomUUID();
    when(mailboxService.get(mailboxId, userId)).thenThrow(new MailboxNotFoundException(mailboxId));

    mockMvc
        .perform(get("/api/ai/mailboxes/{mailboxId}", mailboxId).principal(authentication))
        .andExpect(status().isNotFound());
  }

  @Test
  void createReturns201WithCreatedMailbox() throws Exception {
    when(authentication.getPrincipal()).thenReturn(userId.toString());
    CreateMailboxRequest request =
        new CreateMailboxRequest("lawyer@example.com", "secret-pass", null, null, null, null);
    UUID mailboxId = UUID.randomUUID();
    when(mailboxService.create(eq(request), eq(userId))).thenReturn(sampleMailbox(mailboxId));

    mockMvc
        .perform(
            post("/api/ai/mailboxes")
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(mailboxId.toString()));
  }

  @Test
  void createReturns400OnInvalidEmail() throws Exception {
    CreateMailboxRequest request =
        new CreateMailboxRequest("not-an-email", "secret-pass", null, null, null, null);

    mockMvc
        .perform(
            post("/api/ai/mailboxes")
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void updateReturnsUpdatedMailbox() throws Exception {
    when(authentication.getPrincipal()).thenReturn(userId.toString());
    UUID mailboxId = UUID.randomUUID();
    UpdateMailboxRequest request = new UpdateMailboxRequest(null, null, null, null, null, false);
    when(mailboxService.update(eq(mailboxId), eq(request), eq(userId)))
        .thenReturn(sampleMailbox(mailboxId));

    mockMvc
        .perform(
            put("/api/ai/mailboxes/{mailboxId}", mailboxId)
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(mailboxId.toString()));
  }

  @Test
  void updateReturns400OnInvalidPort() throws Exception {
    String invalidBody =
        "{\"password\":null,\"imapHost\":null,\"imapPort\":999999,\"imapSsl\":null,"
            + "\"folder\":null,\"syncEnabled\":null}";

    mockMvc
        .perform(
            put("/api/ai/mailboxes/{mailboxId}", UUID.randomUUID())
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidBody))
        .andExpect(status().isBadRequest());
  }

  @Test
  void testConnectionReturnsResult() throws Exception {
    when(authentication.getPrincipal()).thenReturn(userId.toString());
    UUID mailboxId = UUID.randomUUID();
    when(mailboxService.testConnection(mailboxId, userId)).thenReturn(MailboxTestResult.ok());

    mockMvc
        .perform(post("/api/ai/mailboxes/{mailboxId}/test", mailboxId).principal(authentication))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));
  }

  @Test
  void syncReturnsSyncResult() throws Exception {
    when(authentication.getPrincipal()).thenReturn(userId.toString());
    UUID mailboxId = UUID.randomUUID();
    when(mailSyncService.syncMailboxForUser(mailboxId, userId))
        .thenReturn(MailSyncResult.ok(mailboxId, 5, 3, true));

    mockMvc
        .perform(post("/api/ai/mailboxes/{mailboxId}/sync", mailboxId).principal(authentication))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.fetched").value(5))
        .andExpect(jsonPath("$.saved").value(3));
  }

  @Test
  void deleteReturns204() throws Exception {
    when(authentication.getPrincipal()).thenReturn(userId.toString());
    UUID mailboxId = UUID.randomUUID();

    mockMvc
        .perform(delete("/api/ai/mailboxes/{mailboxId}", mailboxId).principal(authentication))
        .andExpect(status().isNoContent());
  }

  @Test
  void anyEndpointReturns500WhenUnauthenticated() throws Exception {
    mockMvc.perform(get("/api/ai/mailboxes")).andExpect(status().is5xxServerError());
  }
}
