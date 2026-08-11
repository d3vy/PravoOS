package com.pravoos.ai.shared.mail;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pravoos.ai.shared.exception.MailboxHostNotAllowedException;
import org.junit.jupiter.api.Test;

class MailHostGuardTest {

  private final MailHostGuard guard = new MailHostGuard();

  @Test
  void rejectsLoopbackHost() {
    assertThatThrownBy(() -> guard.requireRoutableHost("localhost"))
        .isInstanceOf(MailboxHostNotAllowedException.class);
    assertThatThrownBy(() -> guard.requireRoutableHost("127.0.0.1"))
        .isInstanceOf(MailboxHostNotAllowedException.class);
  }

  @Test
  void rejectsPrivateNetworkHostsUsedByOtherServices() {
    assertThatThrownBy(() -> guard.requireRoutableHost("10.0.0.5"))
        .isInstanceOf(MailboxHostNotAllowedException.class);
    assertThatThrownBy(() -> guard.requireRoutableHost("192.168.1.10"))
        .isInstanceOf(MailboxHostNotAllowedException.class);
    assertThatThrownBy(() -> guard.requireRoutableHost("172.16.0.1"))
        .isInstanceOf(MailboxHostNotAllowedException.class);
  }

  @Test
  void rejectsCloudMetadataAndSharedAddressSpace() {
    assertThatThrownBy(() -> guard.requireRoutableHost("169.254.169.254"))
        .isInstanceOf(MailboxHostNotAllowedException.class);
    assertThatThrownBy(() -> guard.requireRoutableHost("100.64.0.1"))
        .isInstanceOf(MailboxHostNotAllowedException.class);
  }

  @Test
  void rejectsEmptyAndOversizedHosts() {
    assertThatThrownBy(() -> guard.requireRoutableHost("  "))
        .isInstanceOf(MailboxHostNotAllowedException.class);
    assertThatThrownBy(() -> guard.requireRoutableHost(null))
        .isInstanceOf(MailboxHostNotAllowedException.class);
    assertThatThrownBy(() -> guard.requireRoutableHost("a".repeat(256) + ".example.com"))
        .isInstanceOf(MailboxHostNotAllowedException.class);
  }

  @Test
  void allowsPublicMailHost() {
    assertThatCode(() -> guard.requireRoutableHost("93.184.216.34")).doesNotThrowAnyException();
  }
}
