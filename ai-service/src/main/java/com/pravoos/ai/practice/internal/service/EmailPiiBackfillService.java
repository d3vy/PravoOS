package com.pravoos.ai.practice.internal.service;

import com.pravoos.common.security.PiiEncryptor;
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
public class EmailPiiBackfillService {

  private static final Logger log = LoggerFactory.getLogger(EmailPiiBackfillService.class);
  private static final int BATCH_SIZE = 200;
  private static final String SELECT_BATCH =
      "SELECT id, from_address, to_addresses, cc_addresses, subject, body_text "
          + "FROM email_messages WHERE id > ? ORDER BY id LIMIT "
          + BATCH_SIZE;
  private static final String UPDATE_ROW =
      "UPDATE email_messages SET from_address = ?, to_addresses = ?, cc_addresses = ?, "
          + "subject = ?, body_text = ? WHERE id = ?";

  private final JdbcTemplate jdbcTemplate;
  private final PiiEncryptor piiEncryptor;
  private final boolean backfillOnStart;
  private final EmailPiiBackfillService self;

  public EmailPiiBackfillService(
      JdbcTemplate jdbcTemplate,
      PiiEncryptor piiEncryptor,
      @Value("${pii.crypto.backfill-on-start:false}") boolean backfillOnStart,
      @Lazy EmailPiiBackfillService self) {
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
      log.warn("Email PII backfill requested but encryption is disabled — skipping");
      return;
    }
    UUID cursor = new UUID(0L, 0L);
    int total = 0;
    while (cursor != null) {
      BatchResult result = self.backfillBatch(cursor);
      total += result.updated();
      cursor = result.lastId();
    }
    log.info("Email PII backfill complete: {} message(s) re-encrypted", total);
  }

  @Transactional
  public BatchResult backfillBatch(UUID cursor) {
    List<Map<String, Object>> rows = jdbcTemplate.queryForList(SELECT_BATCH, cursor);
    if (rows.isEmpty()) {
      return new BatchResult(0, null);
    }
    int updated = 0;
    UUID lastId = null;
    for (Map<String, Object> row : rows) {
      lastId = (UUID) row.get("id");
      if (reEncryptRow(row, lastId)) {
        updated++;
      }
    }
    return new BatchResult(updated, lastId);
  }

  private boolean reEncryptRow(Map<String, Object> row, UUID id) {
    String fromAddress = (String) row.get("from_address");
    String toAddresses = (String) row.get("to_addresses");
    String ccAddresses = (String) row.get("cc_addresses");
    String subject = (String) row.get("subject");
    String bodyText = (String) row.get("body_text");

    String newFrom = reEncrypt(fromAddress);
    String newTo = reEncrypt(toAddresses);
    String newCc = reEncrypt(ccAddresses);
    String newSubject = reEncrypt(subject);
    String newBody = reEncrypt(bodyText);

    if (Objects.equals(newFrom, fromAddress)
        && Objects.equals(newTo, toAddresses)
        && Objects.equals(newCc, ccAddresses)
        && Objects.equals(newSubject, subject)
        && Objects.equals(newBody, bodyText)) {
      return false;
    }
    jdbcTemplate.update(UPDATE_ROW, newFrom, newTo, newCc, newSubject, newBody, id);
    return true;
  }

  private String reEncrypt(String stored) {
    if (stored == null || piiEncryptor.isEncryptedWithActiveKey(stored)) {
      return stored;
    }
    return piiEncryptor.encrypt(piiEncryptor.decrypt(stored));
  }

  public record BatchResult(int updated, UUID lastId) {}
}
