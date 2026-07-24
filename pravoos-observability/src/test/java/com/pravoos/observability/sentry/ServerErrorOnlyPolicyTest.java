package com.pravoos.observability.sentry;

import static org.assertj.core.api.Assertions.assertThat;

import com.pravoos.common.exception.HttpStatusCarrier;
import io.sentry.SentryEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ServerErrorOnlyPolicyTest {

  private ServerErrorOnlyPolicy policy;

  @BeforeEach
  void setup() {
    policy = new ServerErrorOnlyPolicy();
  }

  @Test
  void dropsClientErrors() {
    assertThat(policy.shouldReport(new SentryEvent(new StatusException(403)))).isFalse();
  }

  @Test
  void reportsServerErrors() {
    assertThat(policy.shouldReport(new SentryEvent(new StatusException(503)))).isTrue();
  }

  @Test
  void unwrapsCauseChain() {
    RuntimeException wrapper = new RuntimeException("wrapped", new StatusException(422));

    assertThat(policy.shouldReport(new SentryEvent(wrapper))).isFalse();
  }

  @Test
  void reportsUnknownFailures() {
    assertThat(policy.shouldReport(new SentryEvent(new IllegalStateException("boom")))).isTrue();
    assertThat(policy.shouldReport(new SentryEvent())).isTrue();
  }

  private static class StatusException extends RuntimeException implements HttpStatusCarrier {

    private final int status;

    StatusException(int status) {
      super("status " + status);
      this.status = status;
    }

    @Override
    public int httpStatusCode() {
      return status;
    }
  }
}
