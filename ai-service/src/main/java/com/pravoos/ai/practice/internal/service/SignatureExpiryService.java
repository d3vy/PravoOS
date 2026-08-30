package com.pravoos.ai.practice.internal.service;

import com.pravoos.ai.practice.internal.model.entity.SignatureRequest;
import com.pravoos.ai.practice.internal.repository.jpa.SignatureRequestRepository;
import com.pravoos.ai.shared.model.enums.SignatureStatus;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SignatureExpiryService {

  private static final Logger log = LoggerFactory.getLogger(SignatureExpiryService.class);
  private static final int PAGE_SIZE = 1000;

  private final SignatureRequestRepository signatureRequestRepository;

  public SignatureExpiryService(SignatureRequestRepository signatureRequestRepository) {
    this.signatureRequestRepository = signatureRequestRepository;
  }

  @Scheduled(cron = "${signature.expiry.cron:0 25 3 * * *}", zone = "UTC")
  @SchedulerLock(
      name = "SignatureExpiryService_sweepExpired",
      lockAtLeastFor = "PT1M",
      lockAtMostFor = "PT15M")
  @Transactional
  public int sweepExpired() {
    LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
    int total = 0;
    Pageable firstPage = PageRequest.of(0, PAGE_SIZE);
    List<SignatureRequest> expired;
    do {
      expired =
          signatureRequestRepository
              .findByStatusAndExpiresAtBefore(SignatureStatus.PENDING, now, firstPage)
              .getContent();
      for (SignatureRequest request : expired) {
        request.setStatus(SignatureStatus.EXPIRED);
      }
      if (!expired.isEmpty()) {
        signatureRequestRepository.saveAll(expired);
        total += expired.size();
      }
    } while (expired.size() == PAGE_SIZE);
    if (total > 0) {
      log.info("Signature expiry sweep marked {} requests as EXPIRED", total);
    }
    return total;
  }
}
