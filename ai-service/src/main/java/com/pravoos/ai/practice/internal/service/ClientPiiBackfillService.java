package com.pravoos.ai.practice.internal.service;

import com.pravoos.ai.shared.security.PiiEncryptor;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClientPiiBackfillService {

  private static final Logger log = LoggerFactory.getLogger(ClientPiiBackfillService.class);

  private final JdbcTemplate jdbcTemplate;
  private final PiiEncryptor piiEncryptor;
  private final boolean backfillOnStart;
  private final ClientPiiBackfillService self;

  public ClientPiiBackfillService(
      JdbcTemplate jdbcTemplate,
      PiiEncryptor piiEncryptor,
      @Value("${pii.crypto.backfill-on-start:false}") boolean backfillOnStart,
      @Lazy ClientPiiBackfillService self) {
    this.jdbcTemplate = jdbcTemplate;
    this.piiEncryptor = piiEncryptor;
    this.backfillOnStart = backfillOnStart;
    this.self = self;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void backfillOnStart() {
    if (!backfillOnStart) {
      return;
    }
    if (!piiEncryptor.isEncryptionEnabled()) {
      log.warn("PII backfill requested but encryption is disabled — skipping");
      return;
    }
    int updated = self.backfill();
    log.info("PII backfill complete: {} client row(s) re-encrypted", updated);
  }

  @Transactional
  public int backfill() {
    List<Map<String, Object>> rows =
        jdbcTemplate.queryForList("SELECT id, name, phone, email, notes FROM clients");
    int updated = 0;
    for (Map<String, Object> row : rows) {
      String name = (String) row.get("name");
      String phone = (String) row.get("phone");
      String email = (String) row.get("email");
      String notes = (String) row.get("notes");

      String newName = reEncrypt(name);
      String newPhone = reEncrypt(phone);
      String newEmail = reEncrypt(email);
      String newNotes = reEncrypt(notes);

      if (Objects.equals(newName, name)
          && Objects.equals(newPhone, phone)
          && Objects.equals(newEmail, email)
          && Objects.equals(newNotes, notes)) {
        continue;
      }
      jdbcTemplate.update(
          "UPDATE clients SET name = ?, phone = ?, email = ?, notes = ? WHERE id = ?",
          newName,
          newPhone,
          newEmail,
          newNotes,
          (UUID) row.get("id"));
      updated++;
    }
    return updated;
  }

  private String reEncrypt(String stored) {
    if (stored == null || piiEncryptor.isEncryptedWithActiveKey(stored)) {
      return stored;
    }
    return piiEncryptor.encrypt(piiEncryptor.decrypt(stored));
  }
}
