package com.pravoos.ai.practice.internal.service;

import com.pravoos.ai.practice.internal.dto.CreateMailboxRequest;
import com.pravoos.ai.practice.internal.dto.MailHostPresetResponse;
import com.pravoos.ai.practice.internal.dto.MailboxResponse;
import com.pravoos.ai.practice.internal.dto.MailboxTestResult;
import com.pravoos.ai.practice.internal.dto.UpdateMailboxRequest;
import com.pravoos.ai.practice.internal.model.entity.Mailbox;
import com.pravoos.ai.practice.internal.repository.jpa.MailboxRepository;
import com.pravoos.ai.shared.config.MailboxProperties;
import com.pravoos.ai.shared.exception.MailboxAlreadyExistsException;
import com.pravoos.ai.shared.exception.MailboxConnectionException;
import com.pravoos.ai.shared.exception.MailboxHostRequiredException;
import com.pravoos.ai.shared.exception.MailboxLimitExceededException;
import com.pravoos.ai.shared.exception.MailboxNotFoundException;
import com.pravoos.ai.shared.mail.MailHostPreset;
import com.pravoos.ai.shared.mail.MailboxCredentials;
import com.pravoos.ai.shared.mail.MailboxReader;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MailboxService {

  private static final Logger log = LoggerFactory.getLogger(MailboxService.class);
  private static final String DEFAULT_FOLDER = "INBOX";

  private final MailboxRepository mailboxRepository;
  private final MailboxReader mailboxReader;
  private final MailboxProperties mailboxProperties;

  public MailboxService(
      MailboxRepository mailboxRepository,
      MailboxReader mailboxReader,
      MailboxProperties mailboxProperties) {
    this.mailboxRepository = mailboxRepository;
    this.mailboxReader = mailboxReader;
    this.mailboxProperties = mailboxProperties;
  }

  public List<MailHostPresetResponse> presets() {
    return Arrays.stream(MailHostPreset.values()).map(MailHostPresetResponse::from).toList();
  }

  @Transactional(readOnly = true)
  public List<MailboxResponse> findByUser(UUID userId) {
    return mailboxRepository.findByUserIdOrderByCreatedAtAsc(userId).stream()
        .map(MailboxResponse::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public MailboxResponse get(UUID mailboxId, UUID userId) {
    return MailboxResponse.from(requireOwnedMailbox(mailboxId, userId));
  }

  @Transactional
  public MailboxResponse create(CreateMailboxRequest request, UUID userId) {
    String emailAddress = normalizeEmail(request.emailAddress());
    if (mailboxRepository.existsByUserIdAndEmailAddress(userId, emailAddress)) {
      throw new MailboxAlreadyExistsException(emailAddress);
    }
    if (mailboxRepository.countByUserId(userId) >= mailboxProperties.maxPerUser()) {
      throw new MailboxLimitExceededException(mailboxProperties.maxPerUser());
    }

    Optional<MailHostPreset> preset = MailHostPreset.forEmail(emailAddress);
    String imapHost = firstNonBlank(request.imapHost(), preset.map(MailHostPreset::getImapHost));
    Integer imapPort =
        request.imapPort() != null
            ? request.imapPort()
            : preset.map(MailHostPreset::getImapPort).orElse(null);
    if (imapHost == null || imapPort == null) {
      throw new MailboxHostRequiredException(emailAddress);
    }

    Mailbox mailbox = new Mailbox();
    mailbox.setUserId(userId);
    mailbox.setEmailAddress(emailAddress);
    mailbox.setImapHost(imapHost);
    mailbox.setImapPort(imapPort);
    mailbox.setImapSsl(
        request.imapSsl() != null
            ? request.imapSsl()
            : preset.map(MailHostPreset::isSsl).orElse(true));
    mailbox.setPassword(request.password());
    mailbox.setFolder(normalizeFolder(request.folder()));

    Mailbox saved = mailboxRepository.save(mailbox);
    log.info("Mailbox {} connected for user {}", saved.getId(), userId);
    return MailboxResponse.from(saved);
  }

  @Transactional
  public MailboxResponse update(UUID mailboxId, UpdateMailboxRequest request, UUID userId) {
    Mailbox mailbox = requireOwnedMailbox(mailboxId, userId);
    boolean connectionChanged = false;

    if (request.password() != null && !request.password().isBlank()) {
      mailbox.setPassword(request.password());
      connectionChanged = true;
    }
    if (request.imapHost() != null && !request.imapHost().isBlank()) {
      mailbox.setImapHost(request.imapHost().trim());
      connectionChanged = true;
    }
    if (request.imapPort() != null) {
      mailbox.setImapPort(request.imapPort());
      connectionChanged = true;
    }
    if (request.imapSsl() != null) {
      mailbox.setImapSsl(request.imapSsl());
      connectionChanged = true;
    }
    if (request.folder() != null && !request.folder().isBlank()) {
      mailbox.setFolder(normalizeFolder(request.folder()));
      connectionChanged = true;
    }
    if (request.syncEnabled() != null) {
      mailbox.setSyncEnabled(request.syncEnabled());
    }
    if (connectionChanged) {
      mailbox.resetVerification();
    }

    log.info("Mailbox {} updated by user {}", mailboxId, userId);
    return MailboxResponse.from(mailboxRepository.save(mailbox));
  }

  @Transactional
  public MailboxTestResult testConnection(UUID mailboxId, UUID userId) {
    Mailbox mailbox = requireOwnedMailbox(mailboxId, userId);
    try {
      mailboxReader.verifyConnection(credentialsOf(mailbox));
      mailbox.markConnected();
      mailboxRepository.save(mailbox);
      log.info("Mailbox {} connection verified", mailboxId);
      return MailboxTestResult.ok();
    } catch (MailboxConnectionException e) {
      mailbox.markFailed(e.getMessage());
      mailboxRepository.save(mailbox);
      log.warn("Mailbox {} connection failed: {}", mailboxId, e.getMessage());
      return MailboxTestResult.failed(e.getMessage());
    }
  }

  @Transactional
  public void delete(UUID mailboxId, UUID userId) {
    Mailbox mailbox = requireOwnedMailbox(mailboxId, userId);
    mailboxRepository.delete(mailbox);
    log.info("Mailbox {} deleted by user {}", mailboxId, userId);
  }

  private MailboxCredentials credentialsOf(Mailbox mailbox) {
    return new MailboxCredentials(
        mailbox.getImapHost(),
        mailbox.getImapPort(),
        mailbox.isImapSsl(),
        mailbox.getEmailAddress(),
        mailbox.getPassword(),
        mailbox.getFolder());
  }

  private Mailbox requireOwnedMailbox(UUID mailboxId, UUID userId) {
    return mailboxRepository
        .findByIdAndUserId(mailboxId, userId)
        .orElseThrow(() -> new MailboxNotFoundException(mailboxId));
  }

  private String normalizeEmail(String emailAddress) {
    return emailAddress.trim().toLowerCase(Locale.ROOT);
  }

  private String normalizeFolder(String folder) {
    return folder == null || folder.isBlank() ? DEFAULT_FOLDER : folder.trim();
  }

  private String firstNonBlank(String explicit, Optional<String> fallback) {
    if (explicit != null && !explicit.isBlank()) {
      return explicit.trim();
    }
    return fallback.orElse(null);
  }
}
