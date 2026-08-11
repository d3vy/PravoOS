package com.pravoos.common.security.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pravoos.common.security.internal.InternalCallerProperties.Caller;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InternalCallerVerifierTest {

  private static final String AI_SECRET = "ai-secret-value";
  private static final String NOTIFICATION_SECRET = "notification-secret-value";

  private final InternalCallerVerifier verifier =
      new InternalCallerVerifier(
          new InternalCallerProperties(
              Map.of(
                  "ai-service",
                  new Caller(AI_SECRET, List.of("/internal/portal-invites")),
                  "notification-service",
                  new Caller(NOTIFICATION_SECRET, List.of("/internal/applications/")))));

  @Test
  void authorizesKnownCallerOnAllowedPath() {
    assertThat(verifier.authorize("ai-service", AI_SECRET, "/internal/portal-invites/status"))
        .isTrue();
  }

  @Test
  void rejectsCallerReachingBeyondItsAllowedPaths() {
    assertThat(verifier.authorize("ai-service", AI_SECRET, "/internal/applications/1/approve"))
        .isFalse();
  }

  @Test
  void rejectsCallerPresentingAnotherServicesSecret() {
    assertThat(verifier.authorize("ai-service", NOTIFICATION_SECRET, "/internal/portal-invites"))
        .isFalse();
  }

  @Test
  void rejectsUnknownAndMissingCaller() {
    assertThat(verifier.authorize("ghost-service", AI_SECRET, "/internal/portal-invites"))
        .isFalse();
    assertThat(verifier.authorize(null, AI_SECRET, "/internal/portal-invites")).isFalse();
  }

  @Test
  void rejectsMissingSecret() {
    assertThat(verifier.authorize("ai-service", null, "/internal/portal-invites")).isFalse();
    assertThat(verifier.authorize("ai-service", "", "/internal/portal-invites")).isFalse();
  }

  @Test
  void rejectsEveryCallWhenSecretIsNotConfigured() {
    InternalCallerVerifier unconfigured =
        new InternalCallerVerifier(
            new InternalCallerProperties(
                Map.of("ai-service", new Caller("  ", List.of("/internal/")))));

    assertThat(unconfigured.authorize("ai-service", "  ", "/internal/portal-invites")).isFalse();
  }

  @Test
  void startupFailsWhenAnyCallerSecretIsMissing() {
    InternalCallerProperties properties =
        new InternalCallerProperties(
            Map.of(
                "ai-service",
                new Caller(AI_SECRET, List.of("/internal/")),
                "notification-service",
                new Caller("", List.of("/internal/"))));

    assertThatThrownBy(() -> InternalCallerCheck.requireEveryCallerConfigured(properties))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("notification-service");
  }

  @Test
  void startupFailsWhenNoCallersAreConfigured() {
    assertThatThrownBy(
            () ->
                InternalCallerCheck.requireEveryCallerConfigured(
                    new InternalCallerProperties(null)))
        .isInstanceOf(IllegalStateException.class);
  }
}
