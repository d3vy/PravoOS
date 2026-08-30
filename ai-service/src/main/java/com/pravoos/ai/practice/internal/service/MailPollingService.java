package com.pravoos.ai.practice.internal.service;

import com.pravoos.ai.practice.internal.dto.MailSyncResult;
import com.pravoos.ai.practice.internal.repository.jpa.MailboxRepository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class MailPollingService {

  private static final Logger log = LoggerFactory.getLogger(MailPollingService.class);
  private static final int PAGE_SIZE = 500;

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
    LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
    int total = 0;
    int failed = 0;
    int saved = 0;
    Pageable pageable = PageRequest.of(0, PAGE_SIZE);
    Page<UUID> page;
    do {
      page = mailboxRepository.findIdsDueForSync(now, pageable);
      for (UUID mailboxId : page) {
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
      total += page.getNumberOfElements();
      pageable = pageable.next();
    } while (page.hasNext());
    if (total > 0) {
      log.info("Синк почты завершён: {} ящиков, новых писем {}, ошибок {}", total, saved, failed);
    }
  }
}
