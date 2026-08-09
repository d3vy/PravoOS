package com.pravoos.notification.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

class UserServiceClientTest {

  private static final String BASE_URL = "http://user-service.test";
  private static final String SECRET = "internal-secret-1";

  private MockRestServiceServer server;
  private UserServiceClient client;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder =
        RestClient.builder().baseUrl(BASE_URL).defaultHeader("X-Internal-Secret", SECRET);
    server = MockRestServiceServer.bindTo(builder).build();
    client = new UserServiceClient(builder.build());
  }

  @Test
  void sendsTheInternalSecretOnEveryCallWithoutEachMethodRepeatingIt() {
    UUID applicationId = UUID.randomUUID();
    server
        .expect(requestTo(BASE_URL + "/internal/applications/" + applicationId + "/approve"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("X-Internal-Secret", SECRET))
        .andRespond(withSuccess());

    client.approveApplication(applicationId);

    server.verify();
  }

  @Test
  void returnsAnEmptyApplicationWhenUserServiceReports404() {
    UUID applicationId = UUID.randomUUID();
    server
        .expect(requestTo(BASE_URL + "/internal/applications/" + applicationId))
        .andRespond(withStatus(HttpStatus.NOT_FOUND));

    assertThat(client.getApplication(applicationId)).isEmpty();
  }

  @Test
  void propagatesServerErrorsWhenFetchingAnApplicationRatherThanSwallowingThem() {
    UUID applicationId = UUID.randomUUID();
    server
        .expect(requestTo(BASE_URL + "/internal/applications/" + applicationId))
        .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

    assertThatThrownBy(() -> client.getApplication(applicationId))
        .isInstanceOf(HttpServerErrorException.class);
  }

  @Test
  void translatesA4xxOnTelegramBindIntoAUserFacingMessage() {
    server
        .expect(requestTo(BASE_URL + "/internal/telegram/bind"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(jsonPath("$.code").value("bad-code"))
        .andExpect(jsonPath("$.chatId").value(42))
        .andRespond(withStatus(HttpStatus.BAD_REQUEST));

    assertThatThrownBy(() -> client.bindTelegram("bad-code", 42L))
        .isInstanceOf(TelegramBindException.class)
        .hasMessageContaining("Код недействителен");
  }

  @Test
  void returnsTheBoundFullNameOnSuccessfulTelegramBind() {
    server
        .expect(requestTo(BASE_URL + "/internal/telegram/bind"))
        .andRespond(withSuccess("{\"fullName\":\"Иванов Иван\"}", MediaType.APPLICATION_JSON));

    assertThat(client.bindTelegram("good-code", 42L)).isEqualTo("Иванов Иван");
  }

  @Test
  void fallsBackToAnEmptyListWhenPushSubscriptionsComeBackNull() {
    UUID userId = UUID.randomUUID();
    server
        .expect(requestTo(BASE_URL + "/internal/push/subscriptions/" + userId))
        .andRespond(withSuccess("null", MediaType.APPLICATION_JSON));

    assertThat(client.listPushSubscriptions(userId)).isEmpty();
  }

  @Test
  void deserialisesPushSubscriptionsIntoTypedResults() {
    UUID userId = UUID.randomUUID();
    server
        .expect(requestTo(BASE_URL + "/internal/push/subscriptions/" + userId))
        .andRespond(
            withSuccess(
                "[{\"endpoint\":\"https://push.test/a\",\"p256dh\":\"key\",\"auth\":\"auth\"}]",
                MediaType.APPLICATION_JSON));

    List<PushSubscriptionResponse> subscriptions = client.listPushSubscriptions(userId);

    assertThat(subscriptions).hasSize(1);
    assertThat(subscriptions.get(0).endpoint()).isEqualTo("https://push.test/a");
  }

  @Test
  void resolvesAnEmptyChatIdWhenTheLawyerHasNoTelegramBinding() {
    UUID lawyerId = UUID.randomUUID();
    server
        .expect(requestTo(BASE_URL + "/internal/telegram/chat-id/" + lawyerId))
        .andRespond(withStatus(HttpStatus.NOT_FOUND));

    assertThat(client.resolveTelegramChatId(lawyerId)).isEmpty();
  }

  @Test
  void resolvesAnEmptyChatIdWhenTheResponseCarriesANullId() {
    UUID lawyerId = UUID.randomUUID();
    server
        .expect(requestTo(BASE_URL + "/internal/telegram/chat-id/" + lawyerId))
        .andRespond(withSuccess("{\"chatId\":null}", MediaType.APPLICATION_JSON));

    assertThat(client.resolveTelegramChatId(lawyerId)).isEmpty();
  }

  @Test
  void resolvesThePresentChatId() {
    UUID lawyerId = UUID.randomUUID();
    server
        .expect(requestTo(BASE_URL + "/internal/telegram/chat-id/" + lawyerId))
        .andRespond(withSuccess("{\"chatId\":777}", MediaType.APPLICATION_JSON));

    assertThat(client.resolveTelegramChatId(lawyerId)).contains(777L);
  }

  @Test
  void fallsBackToAnEmptyDispatchResultWhenTheBodyIsNull() {
    server
        .expect(requestTo(BASE_URL + "/internal/notifications/case-message"))
        .andRespond(withSuccess("null", MediaType.APPLICATION_JSON));

    CaseMessageNotificationResult result =
        client.dispatchCaseMessage(
            new CaseMessageNotificationRequest(
                UUID.randomUUID(),
                "Спор о поставке",
                "LAWYER",
                UUID.randomUUID(),
                null,
                "Текст сообщения"));

    assertThat(result).isEqualTo(CaseMessageNotificationResult.none());
  }
}
