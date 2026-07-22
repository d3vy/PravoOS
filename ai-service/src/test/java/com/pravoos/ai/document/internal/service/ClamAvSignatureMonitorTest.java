package com.pravoos.ai.document.internal.service;

import com.pravoos.ai.shared.config.MalwareScanProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClamAvSignatureMonitorTest {

    private static final DateTimeFormatter CLAMD_TIMESTAMP =
            DateTimeFormatter.ofPattern("EEE MMM d HH:mm:ss yyyy", Locale.ENGLISH);

    private String versionAged(int days) {
        return "ClamAV 1.4.2/27649/" + LocalDate.now().minusDays(days).atStartOfDay().format(CLAMD_TIMESTAMP);
    }

    private ClamAvSignatureMonitor monitor(ClamdClient clamdClient, MalwareScanProperties properties) {
        return new ClamAvSignatureMonitor(clamdClient, properties, new SimpleMeterRegistry());
    }

    private MalwareScanProperties properties(String host, int maxSignatureAgeDays) {
        return new MalwareScanProperties(host, 3310, 1000, false, maxSignatureAgeDays);
    }

    @Test
    void marksSignaturesStaleWhenOlderThanLimit() throws IOException {
        ClamdClient clamdClient = mock(ClamdClient.class);
        when(clamdClient.version()).thenReturn(versionAged(10));
        ClamAvSignatureMonitor monitor = monitor(clamdClient, properties("clamav", 7));

        monitor.refresh();

        assertThat(monitor.signatureAgeDays()).isEqualTo(10);
        assertThat(monitor.signaturesStale()).isTrue();
    }

    @Test
    void keepsFreshSignaturesUsable() throws IOException {
        ClamdClient clamdClient = mock(ClamdClient.class);
        when(clamdClient.version()).thenReturn(versionAged(2));
        ClamAvSignatureMonitor monitor = monitor(clamdClient, properties("clamav", 7));

        monitor.refresh();

        assertThat(monitor.signaturesStale()).isFalse();
    }

    @Test
    void treatsUnreachableScannerAsUnknownAgeInsteadOfStale() throws IOException {
        ClamdClient clamdClient = mock(ClamdClient.class);
        when(clamdClient.version()).thenThrow(new IOException("connection refused"));
        ClamAvSignatureMonitor monitor = monitor(clamdClient, properties("clamav", 7));

        monitor.refresh();

        assertThat(monitor.signatureAgeDays()).isEqualTo(-1);
        assertThat(monitor.signaturesStale()).isFalse();
    }

    @Test
    void neverStaleWhenAgeCheckDisabled() throws IOException {
        ClamdClient clamdClient = mock(ClamdClient.class);
        when(clamdClient.version()).thenReturn(versionAged(400));
        ClamAvSignatureMonitor monitor = monitor(clamdClient, properties("clamav", 0));

        monitor.refresh();

        assertThat(monitor.signaturesStale()).isFalse();
    }

    @Test
    void skipsRefreshWhenScannerDisabled() throws IOException {
        ClamdClient clamdClient = mock(ClamdClient.class);
        ClamAvSignatureMonitor monitor = monitor(clamdClient, properties("", 7));

        monitor.refresh();

        verify(clamdClient, never()).version();
        assertThat(monitor.signaturesStale()).isFalse();
    }
}
