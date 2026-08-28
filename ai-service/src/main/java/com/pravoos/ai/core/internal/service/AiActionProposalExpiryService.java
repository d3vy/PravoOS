package com.pravoos.ai.core.internal.service;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class AiActionProposalExpiryService {

  private final AiActionProposalService proposalService;

  public AiActionProposalExpiryService(AiActionProposalService proposalService) {
    this.proposalService = proposalService;
  }

  @Scheduled(cron = "${agent.proposal-expiry-cron:0 */5 * * * *}", zone = "UTC")
  @SchedulerLock(
      name = "AiActionProposalExpiryService_expireOverdue",
      lockAtLeastFor = "PT30S",
      lockAtMostFor = "PT5M")
  public int expireOverdue() {
    return proposalService.expireOverdue();
  }
}
