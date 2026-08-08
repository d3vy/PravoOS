package com.pravoos.notification.bot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.pravoos.notification.client.UserServiceClient;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.HttpClientErrorException;

@ExtendWith(MockitoExtension.class)
class ApplicationCallbackHandlerTest {

  @Mock private UserServiceClient userServiceClient;

  private ApplicationCallbackHandler handler;

  @BeforeEach
  void setUp() {
    handler = new ApplicationCallbackHandler(userServiceClient);
  }

  @Test
  void supports_recognizesAllThreePrefixes() {
    UUID id = UUID.randomUUID();
    assertThat(handler.supports("approve:" + id)).isTrue();
    assertThat(handler.supports("approve_force:" + id)).isTrue();
    assertThat(handler.supports("reject:" + id)).isTrue();
  }

  @Test
  void supports_rejectsUnknownOrNullCallbacks() {
    assertThat(handler.supports("unknown:abc")).isFalse();
    assertThat(handler.supports(null)).isFalse();
  }

  @Test
  void handle_approve_success() {
    UUID id = UUID.randomUUID();

    ApplicationCallbackResult result = handler.handle("approve:" + id);

    verify(userServiceClient).approveApplication(id);
    assertThat(result.message()).isEqualTo("Заявка принята");
    assertThat(result.outcome()).isEqualTo(ApplicationCallbackResult.Outcome.TERMINAL);
  }

  @Test
  void handle_approve_emailNotVerified_returnsEmailNotVerifiedOutcome() {
    UUID id = UUID.randomUUID();
    doThrow(unprocessableEntity()).when(userServiceClient).approveApplication(id);

    ApplicationCallbackResult result = handler.handle("approve:" + id);

    assertThat(result.outcome()).isEqualTo(ApplicationCallbackResult.Outcome.EMAIL_NOT_VERIFIED);
    assertThat(result.applicationId()).isEqualTo(id);
  }

  @Test
  void handle_approve_notFound_returnsTerminalMessage() {
    UUID id = UUID.randomUUID();
    doThrow(notFound()).when(userServiceClient).approveApplication(id);

    ApplicationCallbackResult result = handler.handle("approve:" + id);

    assertThat(result.message()).isEqualTo("Заявка не найдена");
  }

  @Test
  void handle_approve_conflict_returnsTerminalMessage() {
    UUID id = UUID.randomUUID();
    doThrow(conflict()).when(userServiceClient).approveApplication(id);

    ApplicationCallbackResult result = handler.handle("approve:" + id);

    assertThat(result.message()).isEqualTo("Заявка уже обработана или email занят");
  }

  @Test
  void handle_approveForce_success() {
    UUID id = UUID.randomUUID();

    ApplicationCallbackResult result = handler.handle("approve_force:" + id);

    verify(userServiceClient).approveApplicationForce(id);
    assertThat(result.message()).isEqualTo("Заявка принята без подтверждения почты");
  }

  @Test
  void handle_approveForce_notFound() {
    UUID id = UUID.randomUUID();
    doThrow(notFound()).when(userServiceClient).approveApplicationForce(id);

    ApplicationCallbackResult result = handler.handle("approve_force:" + id);

    assertThat(result.message()).isEqualTo("Заявка не найдена");
  }

  @Test
  void handle_reject_success() {
    UUID id = UUID.randomUUID();

    ApplicationCallbackResult result = handler.handle("reject:" + id);

    verify(userServiceClient).rejectApplication(id);
    assertThat(result.message()).isEqualTo("Заявка отклонена");
  }

  @Test
  void handle_reject_conflict() {
    UUID id = UUID.randomUUID();
    doThrow(conflict()).when(userServiceClient).rejectApplication(id);

    ApplicationCallbackResult result = handler.handle("reject:" + id);

    assertThat(result.message()).isEqualTo("Заявка уже обработана");
  }

  private HttpClientErrorException unprocessableEntity() {
    return (HttpClientErrorException.UnprocessableEntity)
        HttpClientErrorException.create(
            org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
            "Unprocessable",
            new HttpHeaders(),
            new byte[0],
            null);
  }

  private HttpClientErrorException notFound() {
    return (HttpClientErrorException.NotFound)
        HttpClientErrorException.create(
            org.springframework.http.HttpStatus.NOT_FOUND,
            "Not Found",
            new HttpHeaders(),
            new byte[0],
            null);
  }

  private HttpClientErrorException conflict() {
    return (HttpClientErrorException.Conflict)
        HttpClientErrorException.create(
            org.springframework.http.HttpStatus.CONFLICT,
            "Conflict",
            new HttpHeaders(),
            new byte[0],
            null);
  }
}
