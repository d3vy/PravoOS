package com.pravoos.user.registration.internal.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pravoos.user.identity.api.PasswordPolicyService;
import com.pravoos.user.registration.internal.dto.ApplicationResponse;
import com.pravoos.user.registration.internal.dto.ApplicationSubmissionResponse;
import com.pravoos.user.registration.internal.dto.ApplyRequest;
import com.pravoos.user.registration.internal.dto.ResendVerificationRequest;
import com.pravoos.user.registration.internal.dto.UpdateApplicationRequest;
import com.pravoos.user.registration.internal.dto.VerifyEmailRequest;
import com.pravoos.user.registration.internal.model.enums.ApplicationStatus;
import com.pravoos.user.registration.internal.service.ApplicationService;
import com.pravoos.user.registration.internal.service.EmailVerificationService;
import com.pravoos.user.shared.exception.EmailAlreadyExistsException;
import com.pravoos.user.shared.exception.GlobalExceptionHandler;
import com.pravoos.user.shared.service.IpRateLimiter;
import com.pravoos.user.shared.util.EmailDeliverabilityValidator;
import java.time.Duration;
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
class AuthApplicationControllerTest {

  @Mock private ApplicationService applicationService;
  @Mock private EmailVerificationService emailVerificationService;
  @Mock private PasswordPolicyService passwordPolicyService;
  @Mock private EmailDeliverabilityValidator emailDeliverabilityValidator;
  @Mock private IpRateLimiter ipRateLimiter;

  private MockMvc mockMvc;
  private final ObjectMapper objectMapper = new ObjectMapper();

  private static final ApplyRequest VALID_APPLY_REQUEST =
      new ApplyRequest(
          "lawyer@example.com",
          "Иван Иванов",
          "password1",
          "Уголовное право",
          "+79990000000",
          true,
          false,
          false,
          "2.0");

  @BeforeEach
  void setUp() {
    AuthApplicationController controller =
        new AuthApplicationController(
            applicationService,
            emailVerificationService,
            passwordPolicyService,
            emailDeliverabilityValidator,
            ipRateLimiter);
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  void applyReturns201WithApplicationOnSuccess() throws Exception {
    when(ipRateLimiter.allow(eq("apply"), anyString(), eq(5), any(Duration.class)))
        .thenReturn(true);
    UUID appId = UUID.randomUUID();
    ApplicationResponse application =
        new ApplicationResponse(
            appId,
            "lawyer@example.com",
            "Иван Иванов",
            "Уголовное право",
            "+79990000000",
            ApplicationStatus.PENDING,
            null,
            null,
            false);
    when(applicationService.submitApplication(eq(VALID_APPLY_REQUEST), anyString(), any()))
        .thenReturn(new ApplicationSubmissionResponse(application, "status-token"));

    mockMvc
        .perform(
            post("/api/auth/apply")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(VALID_APPLY_REQUEST)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.application.id").value(appId.toString()))
        .andExpect(jsonPath("$.statusToken").value("status-token"));
  }

  @Test
  void applyReturns429WhenIpRateLimitExceeded() throws Exception {
    when(ipRateLimiter.allow(eq("apply"), anyString(), eq(5), any(Duration.class)))
        .thenReturn(false);

    mockMvc
        .perform(
            post("/api/auth/apply")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(VALID_APPLY_REQUEST)))
        .andExpect(status().isTooManyRequests());

    verify(applicationService, never()).submitApplication(any(), anyString(), any());
  }

  @Test
  void applyReturns400ForInvalidBody() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/apply")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new ApplyRequest(
                            "not-an-email", "A", "short", "", "123", false, false, false, null))))
        .andExpect(status().isBadRequest());

    verify(applicationService, never()).submitApplication(any(), anyString(), any());
  }

  @Test
  void applyReturns409WhenEmailAlreadyExists() throws Exception {
    when(ipRateLimiter.allow(eq("apply"), anyString(), eq(5), any(Duration.class)))
        .thenReturn(true);
    when(applicationService.submitApplication(eq(VALID_APPLY_REQUEST), anyString(), any()))
        .thenThrow(new EmailAlreadyExistsException());

    mockMvc
        .perform(
            post("/api/auth/apply")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(VALID_APPLY_REQUEST)))
        .andExpect(status().isConflict());
  }

  @Test
  void getApplicationByStatusTokenReturnsApplication() throws Exception {
    UUID appId = UUID.randomUUID();
    when(applicationService.getApplicationByStatusToken("token-123"))
        .thenReturn(
            new ApplicationResponse(
                appId,
                "lawyer@example.com",
                "Иван Иванов",
                "Уголовное право",
                "+79990000000",
                ApplicationStatus.PENDING,
                null,
                null,
                false));

    mockMvc
        .perform(get("/api/auth/application").header("X-Application-Token", "token-123"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(appId.toString()));
  }

  @Test
  void getApplicationByStatusTokenReturns500WhenHeaderMissing() throws Exception {
    // GlobalExceptionHandler не мапит MissingRequestHeaderException — падает в generic
    // 500-обработчик
    mockMvc.perform(get("/api/auth/application")).andExpect(status().isInternalServerError());
  }

  @Test
  void updateApplicationValidatesEmailAndDelegatesToService() throws Exception {
    UpdateApplicationRequest request =
        new UpdateApplicationRequest(
            "lawyer@example.com", "Иван Иванов", "newpass1", "Уголовное право", "+79990000000");
    UUID appId = UUID.randomUUID();
    when(applicationService.updateApplication(eq("token-123"), eq(request)))
        .thenReturn(
            new ApplicationResponse(
                appId,
                "lawyer@example.com",
                "Иван Иванов",
                "Уголовное право",
                "+79990000000",
                ApplicationStatus.PENDING,
                null,
                null,
                false));

    mockMvc
        .perform(
            put("/api/auth/application")
                .header("X-Application-Token", "token-123")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk());

    verify(passwordPolicyService).validate("newpass1");
    verify(emailDeliverabilityValidator).validate("lawyer@example.com");
  }

  @Test
  void updateApplicationSkipsPasswordValidationWhenPasswordBlank() throws Exception {
    UpdateApplicationRequest request =
        new UpdateApplicationRequest(
            "lawyer@example.com", "Иван Иванов", "", "Уголовное право", "+79990000000");
    UUID appId = UUID.randomUUID();
    when(applicationService.updateApplication(eq("token-123"), eq(request)))
        .thenReturn(
            new ApplicationResponse(
                appId,
                "lawyer@example.com",
                "Иван Иванов",
                "Уголовное право",
                "+79990000000",
                ApplicationStatus.PENDING,
                null,
                null,
                false));

    mockMvc
        .perform(
            put("/api/auth/application")
                .header("X-Application-Token", "token-123")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk());

    verify(passwordPolicyService, never()).validate(anyString());
  }

  @Test
  void verifyEmailReturnsVerifiedTrue() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/verify-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new VerifyEmailRequest("tok"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.verified").value(true));

    verify(emailVerificationService).verifyToken("tok");
  }

  @Test
  void resendVerificationReturns202() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/resend-verification")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new ResendVerificationRequest("lawyer@example.com"))))
        .andExpect(status().isAccepted());

    verify(emailVerificationService).resendVerification("lawyer@example.com");
  }

  @Test
  void resendVerificationReturns400ForInvalidEmail() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/resend-verification")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(new ResendVerificationRequest("not-email"))))
        .andExpect(status().isBadRequest());

    verify(emailVerificationService, never()).resendVerification(anyString());
  }
}
