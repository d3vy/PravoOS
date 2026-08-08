package com.pravoos.notification.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pravoos.notification.client.PushSubscriptionResponse;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Security;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.bouncycastle.jce.interfaces.ECPublicKey;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VapidWebPushSenderTest {

  private static PushSubscriptionResponse subscription;
  private static final PushMessage MESSAGE =
      PushMessage.of("Title", "Body", "https://app.example.com", "tag-1");

  @Mock private PushService pushService;
  @Mock private HttpResponse httpResponse;
  @Mock private StatusLine statusLine;

  private VapidWebPushSender sender;

  @BeforeAll
  static void generateSubscriberKeys() throws Exception {
    Security.addProvider(new BouncyCastleProvider());
    KeyPairGenerator generator = KeyPairGenerator.getInstance("ECDH", "BC");
    generator.initialize(new ECGenParameterSpec("secp256r1"));
    KeyPair keyPair = generator.generateKeyPair();
    byte[] rawPoint = ((ECPublicKey) keyPair.getPublic()).getQ().getEncoded(false);
    byte[] authBytes = new byte[16];
    new SecureRandom().nextBytes(authBytes);
    Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
    subscription =
        new PushSubscriptionResponse(
            "https://push.example.com/very-long-endpoint-identifier-1234567890",
            encoder.encodeToString(rawPoint),
            encoder.encodeToString(authBytes));
  }

  @BeforeEach
  void setUp() {
    sender = new VapidWebPushSender(pushService, new ObjectMapper());
  }

  @Test
  void send_2xxResponse_isDelivered() throws Exception {
    mockStatus(201);

    PushDeliveryStatus status = sender.send(subscription, MESSAGE);

    assertThat(status).isEqualTo(PushDeliveryStatus.DELIVERED);
  }

  @Test
  void send_410Gone_isExpired() throws Exception {
    mockStatus(410);

    PushDeliveryStatus status = sender.send(subscription, MESSAGE);

    assertThat(status).isEqualTo(PushDeliveryStatus.EXPIRED);
  }

  @Test
  void send_404NotFound_isExpired() throws Exception {
    mockStatus(404);

    PushDeliveryStatus status = sender.send(subscription, MESSAGE);

    assertThat(status).isEqualTo(PushDeliveryStatus.EXPIRED);
  }

  @Test
  void send_otherErrorStatus_isFailed() throws Exception {
    mockStatus(500);

    PushDeliveryStatus status = sender.send(subscription, MESSAGE);

    assertThat(status).isEqualTo(PushDeliveryStatus.FAILED);
  }

  @Test
  void send_interrupted_isFailedAndRestoresInterruptFlag() throws Exception {
    when(pushService.send(any(Notification.class))).thenThrow(new InterruptedException());

    PushDeliveryStatus status = sender.send(subscription, MESSAGE);

    assertThat(status).isEqualTo(PushDeliveryStatus.FAILED);
    assertThat(Thread.interrupted()).isTrue();
  }

  @Test
  void send_unexpectedException_isFailed() throws Exception {
    when(pushService.send(any(Notification.class))).thenThrow(new RuntimeException("boom"));

    PushDeliveryStatus status = sender.send(subscription, MESSAGE);

    assertThat(status).isEqualTo(PushDeliveryStatus.FAILED);
  }

  private void mockStatus(int code) throws Exception {
    when(pushService.send(any(Notification.class))).thenReturn(httpResponse);
    when(httpResponse.getStatusLine()).thenReturn(statusLine);
    when(statusLine.getStatusCode()).thenReturn(code);
  }
}
