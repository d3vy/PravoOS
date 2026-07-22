package com.pravoos.ai.shared.signature;

import com.pravoos.ai.shared.exception.InvalidSignatureFileException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DetachedCmsVerifierTest {

    private final DetachedCmsVerifier verifier = new DetachedCmsVerifier();

    private final byte[] document = "Договор оказания юридических услуг №42".getBytes(StandardCharsets.UTF_8);

    @Test
    void verify_returnsCertificateDetails_forValidDetachedSignature() {
        byte[] signature = CmsTestSignatures.detachedSignature(document, "Иванов Иван Иванович");

        CmsSignatureDetails details = verifier.verify(signature, document);

        assertThat(details.signerCommonName()).isEqualTo("Иванов Иван Иванович");
        assertThat(details.certificateSubject()).contains("Иванов Иван Иванович");
        assertThat(details.certificateSerial()).isNotBlank();
        assertThat(details.signatureAlgorithm()).contains("SHA256");
        assertThat(details.signingTime()).isNotNull();
        assertThat(details.certificateValidTo()).isAfter(details.certificateValidFrom());
    }

    @Test
    void verify_acceptsBase64EncodedContainer() {
        byte[] signature = CmsTestSignatures.detachedSignature(document, "Петров Пётр");
        byte[] encoded = Base64.getMimeEncoder().encode(signature);

        assertThat(verifier.verify(encoded, document).signerCommonName()).isEqualTo("Петров Пётр");
    }

    @Test
    void verify_rejectsSignatureOfDifferentContent() {
        byte[] signature = CmsTestSignatures.detachedSignature(document, "Иванов Иван");
        byte[] tampered = "Договор оказания юридических услуг №43".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> verifier.verify(signature, tampered))
                .isInstanceOf(InvalidSignatureFileException.class)
                .hasMessageContaining("не соответствует содержимому");
    }

    @Test
    void verify_rejectsCertificateExpiredBeforeSigning() {
        byte[] signature = CmsTestSignatures.detachedSignature(document, "Сидоров Сидор",
                Date.from(Instant.now().minus(400, ChronoUnit.DAYS)),
                Date.from(Instant.now().minus(30, ChronoUnit.DAYS)));

        assertThatThrownBy(() -> verifier.verify(signature, document))
                .isInstanceOf(InvalidSignatureFileException.class)
                .hasMessageContaining("недействителен");
    }

    @Test
    void verify_rejectsGarbageContainer() {
        assertThatThrownBy(() -> verifier.verify(new byte[]{0x01, 0x02, 0x03, (byte) 0xFF}, document))
                .isInstanceOf(InvalidSignatureFileException.class)
                .hasMessageContaining("CMS/PKCS#7");
    }

    @Test
    void verify_rejectsEmptyFile() {
        assertThatThrownBy(() -> verifier.verify(new byte[0], document))
                .isInstanceOf(InvalidSignatureFileException.class)
                .hasMessageContaining("пуст");
    }
}
