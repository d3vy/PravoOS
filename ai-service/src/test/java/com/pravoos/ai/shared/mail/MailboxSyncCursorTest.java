package com.pravoos.ai.shared.mail;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MailboxSyncCursorTest {

  @Test
  void initialCursorStartsFromFirstUid() {
    assertThat(MailboxSyncCursor.initial().startUid(42L)).isEqualTo(1L);
  }

  @Test
  void continuesAfterLastSeenUidWhenValidityMatches() {
    MailboxSyncCursor cursor = new MailboxSyncCursor(42L, 100L);

    assertThat(cursor.matches(42L)).isTrue();
    assertThat(cursor.startUid(42L)).isEqualTo(101L);
  }

  @Test
  void restartsFromFirstUidWhenValidityChanged() {
    MailboxSyncCursor cursor = new MailboxSyncCursor(42L, 100L);

    assertThat(cursor.matches(43L)).isFalse();
    assertThat(cursor.startUid(43L)).isEqualTo(1L);
  }
}
