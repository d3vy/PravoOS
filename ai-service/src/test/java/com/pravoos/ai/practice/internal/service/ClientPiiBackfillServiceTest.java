package com.pravoos.ai.practice.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pravoos.common.security.PiiCryptoProperties;
import com.pravoos.common.security.PiiEncryptor;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

class ClientPiiBackfillServiceTest {

  private static final String SELECT_CLIENTS =
      "SELECT id, name, phone, email, inn, notes FROM clients";
  private static final String UPDATE_CLIENT =
      "UPDATE clients SET name = ?, phone = ?, email = ?, inn = ?, notes = ? WHERE id = ?";

  private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
  private final String activeKey = randomKey();
  private final String previousKey = randomKey();

  private PiiEncryptor encryptor;

  @BeforeEach
  void setUp() {
    Map<String, String> keys = new LinkedHashMap<>();
    keys.put("old", previousKey);
    keys.put("current", activeKey);
    encryptor = new PiiEncryptor(new PiiCryptoProperties(false, "current", keys, null));
  }

  private static String randomKey() {
    byte[] key = new byte[32];
    new SecureRandom().nextBytes(key);
    return Base64.getEncoder().encodeToString(key);
  }

  private ClientPiiBackfillService service(PiiEncryptor piiEncryptor, boolean backfillOnStart) {
    ClientPiiBackfillService transactionalSelf =
        new ClientPiiBackfillService(jdbcTemplate, piiEncryptor, backfillOnStart, null);
    return new ClientPiiBackfillService(
        jdbcTemplate, piiEncryptor, backfillOnStart, transactionalSelf);
  }

  private Map<String, Object> row(
      String name, String phone, String email, String inn, String notes) {
    Map<String, Object> row = new HashMap<>();
    row.put("id", UUID.randomUUID());
    row.put("name", name);
    row.put("phone", phone);
    row.put("email", email);
    row.put("inn", inn);
    row.put("notes", notes);
    return row;
  }

  private List<Object> capturedUpdateArguments() {
    ArgumentCaptor<Object> arguments = ArgumentCaptor.forClass(Object.class);
    verify(jdbcTemplate)
        .update(
            eq(UPDATE_CLIENT),
            arguments.capture(),
            arguments.capture(),
            arguments.capture(),
            arguments.capture(),
            arguments.capture(),
            arguments.capture());
    return arguments.getAllValues();
  }

  @Test
  void encryptsPlaintextRow() {
    when(jdbcTemplate.queryForList(SELECT_CLIENTS))
        .thenReturn(
            List.of(row("Иванов Иван", "+79990000000", "ivan@mail.ru", "7707083893", "заметка")));

    int updated = service(encryptor, true).backfill();

    assertThat(updated).isEqualTo(1);
    List<Object> stored = capturedUpdateArguments();
    assertThat((String) stored.get(0)).startsWith("pii2:current:");
    assertThat(encryptor.decrypt((String) stored.get(0))).isEqualTo("Иванов Иван");
    assertThat(encryptor.decrypt((String) stored.get(3))).isEqualTo("7707083893");
  }

  @Test
  void skipsRowAlreadyEncryptedWithActiveKey() {
    when(jdbcTemplate.queryForList(SELECT_CLIENTS))
        .thenReturn(
            List.of(
                row(
                    encryptor.encrypt("Иванов Иван"),
                    encryptor.encrypt("+79990000000"),
                    null,
                    null,
                    null)));

    int updated = service(encryptor, true).backfill();

    assertThat(updated).isZero();
    verify(jdbcTemplate, never())
        .update(eq(UPDATE_CLIENT), any(), any(), any(), any(), any(), any());
  }

  @Test
  void reEncryptsRowWrittenWithPreviousKey() {
    PiiEncryptor previous =
        new PiiEncryptor(new PiiCryptoProperties(false, "old", Map.of("old", previousKey), null));
    String legacyName = previous.encrypt("Петров Пётр");
    when(jdbcTemplate.queryForList(SELECT_CLIENTS))
        .thenReturn(List.of(row(legacyName, null, null, null, null)));

    int updated = service(encryptor, true).backfill();

    assertThat(updated).isEqualTo(1);
    String reEncrypted = (String) capturedUpdateArguments().get(0);
    assertThat(reEncrypted).startsWith("pii2:current:");
    assertThat(encryptor.decrypt(reEncrypted)).isEqualTo("Петров Пётр");
  }

  @Test
  void isIdempotent_secondRunUpdatesNothing() {
    Map<String, Object> clientRow = row("Иванов Иван", null, null, null, null);
    when(jdbcTemplate.queryForList(SELECT_CLIENTS)).thenReturn(List.of(clientRow));
    ClientPiiBackfillService service = service(encryptor, true);

    service.backfill();
    clientRow.put("name", capturedUpdateArguments().get(0));

    assertThat(service.backfill()).isZero();
    verify(jdbcTemplate, times(1))
        .update(eq(UPDATE_CLIENT), any(), any(), any(), any(), any(), any());
  }

  @Test
  void doesNotTouchDatabase_whenBackfillDisabledOrEncryptionOff() {
    service(encryptor, false).backfillOnStart();

    PiiEncryptor disabled = new PiiEncryptor(new PiiCryptoProperties(false, null, null, null));
    service(disabled, true).backfillOnStart();

    verifyNoInteractions(jdbcTemplate);
  }
}
