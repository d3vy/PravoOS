package com.pravoos.ai.practice.internal.service;

import com.pravoos.ai.practice.internal.dto.MailSyncResult;
import com.pravoos.ai.practice.internal.model.entity.Mailbox;
import com.pravoos.ai.practice.internal.repository.jpa.MailboxRepository;
import java.util.List;
import java.util.UUID;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class MailPollingService {

  private static final Logger log = LoggerFactory.getLogger(MailPollingService.class);

  private final MailboxRepository mailboxRepository;
  private final MailSyncService mailSyncService;

  public MailPollingService(MailboxRepository mailboxRepository, MailSyncService mailSyncService) {
    this.mailboxRepository = mailboxRepository;
    this.mailSyncService = mailSyncService;
  }

  @Scheduled(cron = "${mailbox.sync.cron:0 */10 * * * *}", zone = "UTC")
  @SchedulerLock(
      name = "MailPollingService_pollMailboxes",
      lockAtLeastFor = "PT1M",
      lockAtMostFor = "PT1H")
  public void pollMailboxes() {
    List<UUID> mailboxIds =
        mailboxRepository.findBySyncEnabledTrue().stream().map(Mailbox::getId).toList();
    if (mailboxIds.isEmpty()) {
      return;
    }
    log.info("Синк почты: обрабатываю {} ящиков", mailboxIds.size());
    int failed = 0;
    int saved = 0;
    for (UUID mailboxId : mailboxIds) {
      try {
        MailSyncResult result = mailSyncService.syncMailbox(mailboxId);
        if (result.success()) {
          saved += result.saved();
        } else {
          failed++;
        }
      } catch (Exception e) {
        failed++;
        log.error("Синк почты: ящик {} не обработан: {}", mailboxId, e.getMessage());
      }
    }
    log.info(
        "Синк почты завершён: {} ящиков, новых писем {}, ошибок {}",
        mailboxIds.size(),
        saved,
        failed);
  }
}
