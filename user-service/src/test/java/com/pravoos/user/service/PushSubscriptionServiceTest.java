package com.pravoos.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.pravoos.user.push.internal.dto.PushSubscriptionView;
import com.pravoos.user.push.internal.dto.RegisterPushSubscriptionRequest;
import com.pravoos.user.push.internal.model.entity.PushSubscription;
import com.pravoos.user.push.internal.repository.PushSubscriptionRepository;
import com.pravoos.user.push.internal.service.PushSubscriptionService;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PushSubscriptionServiceTest {

  private static final String ENDPOINT = "https://fcm.googleapis.com/fcm/send/abc";

  @Mock private PushSubscriptionRepository pushSubscriptionRepository;

  private PushSubscriptionService service;

  @BeforeEach
  void setUp() {
    service = new PushSubscriptionService(pushSubscriptionRepository);
  }

  @Test
  void register_createsSubscription_whenEndpointIsNew() {
    UUID userId = UUID.randomUUID();
    when(pushSubscriptionRepository.findByEndpoint(ENDPOINT)).thenReturn(Optional.empty());
    when(pushSubscriptionRepository.countByUserId(userId)).thenReturn(1L);

    service.register(
        userId, new RegisterPushSubscriptionRequest(ENDPOINT, "p256dh", "auth", "Chrome"));

    ArgumentCaptor<PushSubscription> captor = ArgumentCaptor.forClass(PushSubscription.class);
    verify(pushSubscriptionRepository).save(captor.capture());
    PushSubscription saved = captor.getValue();
    assertThat(saved.getUserId()).isEqualTo(userId);
    assertThat(saved.getEndpoint()).isEqualTo(ENDPOINT);
    assertThat(saved.getP256dhKey()).isEqualTo("p256dh");
    assertThat(saved.getAuthKey()).isEqualTo("auth");
    assertThat(saved.getUserAgent()).isEqualTo("Chrome");
    assertThat(saved.getLastUsedAt()).isNotNull();
  }

  @Test
  void register_reassignsExistingEndpoint_toCurrentUser() {
    UUID previousOwner = UUID.randomUUID();
    UUID newOwner = UUID.randomUUID();
    PushSubscription existing =
        subscription(previousOwner, ENDPOINT, LocalDateTime.now(ZoneOffset.UTC));
    when(pushSubscriptionRepository.findByEndpoint(ENDPOINT)).thenReturn(Optional.of(existing));
    when(pushSubscriptionRepository.countByUserId(newOwner)).thenReturn(1L);

    service.register(
        newOwner, new RegisterPushSubscriptionRequest(ENDPOINT, "new-p256dh", "new-auth", null));

    assertThat(existing.getUserId()).isEqualTo(newOwner);
    assertThat(existing.getP256dhKey()).isEqualTo("new-p256dh");
    assertThat(existing.getUserAgent()).isNull();
    verify(pushSubscriptionRepository).save(existing);
  }

  @Test
  void register_evictsOldestDevices_beyondLimit() {
    UUID userId = UUID.randomUUID();
    when(pushSubscriptionRepository.findByEndpoint(ENDPOINT)).thenReturn(Optional.empty());
    when(pushSubscriptionRepository.countByUserId(userId)).thenReturn(12L);
    List<PushSubscription> newestFirst =
        IntStream.range(0, 12)
            .mapToObj(
                index ->
                    subscription(
                        userId,
                        ENDPOINT + index,
                        LocalDateTime.now(ZoneOffset.UTC).minusDays(index)))
            .toList();
    when(pushSubscriptionRepository.findByUserIdOrderByCreatedAtDesc(userId))
        .thenReturn(newestFirst);

    service.register(userId, new RegisterPushSubscriptionRequest(ENDPOINT, "p256dh", "auth", null));

    ArgumentCaptor<List<PushSubscription>> captor = ArgumentCaptor.forClass(List.class);
    verify(pushSubscriptionRepository).deleteAll(captor.capture());
    assertThat(captor.getValue()).containsExactly(newestFirst.get(10), newestFirst.get(11));
  }

  @Test
  void register_keepsAllDevices_whenWithinLimit() {
    UUID userId = UUID.randomUUID();
    when(pushSubscriptionRepository.findByEndpoint(ENDPOINT)).thenReturn(Optional.empty());
    when(pushSubscriptionRepository.countByUserId(userId)).thenReturn(10L);

    service.register(userId, new RegisterPushSubscriptionRequest(ENDPOINT, "p256dh", "auth", null));

    verify(pushSubscriptionRepository, never()).deleteAll(any());
  }

  @Test
  void unregister_deletesOnlyOwnSubscription() {
    UUID userId = UUID.randomUUID();
    when(pushSubscriptionRepository.deleteByEndpointAndUserId(ENDPOINT, userId)).thenReturn(1);

    service.unregister(userId, ENDPOINT);

    verify(pushSubscriptionRepository).deleteByEndpointAndUserId(ENDPOINT, userId);
    verify(pushSubscriptionRepository, never()).deleteByEndpoint(anyString());
  }

  @Test
  void prune_deletesByEndpoint() {
    when(pushSubscriptionRepository.deleteByEndpoint(ENDPOINT)).thenReturn(1);

    service.prune(ENDPOINT);

    verify(pushSubscriptionRepository).deleteByEndpoint(ENDPOINT);
  }

  @Test
  void subscriptionsOf_mapsKeysForDelivery() {
    UUID userId = UUID.randomUUID();
    when(pushSubscriptionRepository.findByUserIdOrderByCreatedAtDesc(userId))
        .thenReturn(List.of(subscription(userId, ENDPOINT, LocalDateTime.now(ZoneOffset.UTC))));

    List<PushSubscriptionView> views = service.subscriptionsOf(userId);

    assertThat(views).containsExactly(new PushSubscriptionView(ENDPOINT, "p256dh", "auth"));
  }

  private PushSubscription subscription(UUID userId, String endpoint, LocalDateTime createdAt) {
    PushSubscription subscription = new PushSubscription();
    subscription.setId(UUID.randomUUID());
    subscription.setUserId(userId);
    subscription.setEndpoint(endpoint);
    subscription.setP256dhKey("p256dh");
    subscription.setAuthKey("auth");
    subscription.setCreatedAt(createdAt);
    return subscription;
  }
}
