package com.pravoos.ai.document.internal.service;

import com.pravoos.ai.shared.config.MalwareScanProperties;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class ClamAvSignatureMonitor {

  private static final Logger log = LoggerFactory.getLogger(ClamAvSignatureMonitor.class);
  private static final int UNKNOWN_AGE = -1;

  private final ClamdClient clamdClient;
  private final MalwareScanProperties properties;

  private volatile int signatureAgeDays = UNKNOWN_AGE;

  public ClamAvSignatureMonitor(
      ClamdClient clamdClient, MalwareScanProperties properties, MeterRegistry meterRegistry) {
    this.clamdClient = clamdClient;
    this.properties = properties;
    Gauge.builder(
            "pravoos.document.malware.signature.age.days",
            this,
            monitor -> monitor.signatureAgeDays)
        .description("Age of the ClamAV signature database in days (-1 when unknown)")
        .register(meterRegistry);
  }

  @Scheduled(
      initialDelayString = "${document.malware-scan.signature-check-initial-delay:PT1M}",
      fixedDelayString = "${document.malware-scan.signature-check-interval:PT1H}")
  public void refresh() {
    if (!properties.enabled()) {
      return;
    }
    String version;
    try {
      version = clamdClient.version();
    } catch (IOException e) {
      signatureAgeDays = UNKNOWN_AGE;
      log.warn("Cannot read ClamAV version, signature freshness unknown: {}", e.getMessage());
      return;
    }

    Optional<LocalDate> signatureDate = ClamdVersionParser.signatureDate(version);
    if (signatureDate.isEmpty()) {
      signatureAgeDays = UNKNOWN_AGE;
      log.warn("Unparsable ClamAV version response, signature freshness unknown: {}", version);
      return;
    }

    signatureAgeDays = (int) ChronoUnit.DAYS.between(signatureDate.get(), LocalDate.now());
    if (signaturesStale()) {
      log.error(
          "ClamAV signatures are {} days old (limit {}), uploads are rejected until freshclam updates them",
          signatureAgeDays,
          properties.maxSignatureAgeDays());
    } else {
      log.debug("ClamAV signatures are {} days old ({})", signatureAgeDays, version);
    }
  }

  public boolean signaturesStale() {
    return properties.maxSignatureAgeDays() > 0
        && signatureAgeDays != UNKNOWN_AGE
        && signatureAgeDays > properties.maxSignatureAgeDays();
  }

  public int signatureAgeDays() {
    return signatureAgeDays;
  }
}
