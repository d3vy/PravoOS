package com.pravoos.ai.shared.signature;

import com.pravoos.ai.shared.exception.InvalidSignatureFileException;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.cms.Attribute;
import org.bouncycastle.asn1.cms.CMSAttributes;
import org.bouncycastle.asn1.cms.Time;
import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x500.style.IETFUtils;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSVerifierCertificateNotValidException;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.DefaultAlgorithmNameFinder;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.util.Store;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Security;
import java.security.cert.CertificateException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Collection;
import java.util.Date;

@Component
public class DetachedCmsVerifier {

    private static final Logger log = LoggerFactory.getLogger(DetachedCmsVerifier.class);

    private static final String PEM_HEADER = "-----BEGIN";
    private static final DefaultAlgorithmNameFinder ALGORITHM_NAMES = new DefaultAlgorithmNameFinder();

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    public CmsSignatureDetails verify(byte[] signatureFile, byte[] documentContent) {
        if (signatureFile == null || signatureFile.length == 0) {
            throw new InvalidSignatureFileException("файл пуст");
        }
        CMSSignedData signedData = parse(decode(signatureFile), documentContent);
        SignerInformation signer = requireSingleSigner(signedData);
        X509CertificateHolder certificate = findCertificate(signedData, signer);

        LocalDateTime signingTime = extractSigningTime(signer);
        assertCertificateValid(certificate, signingTime);
        assertSignatureMatches(signer, certificate);

        return new CmsSignatureDetails(
                commonName(certificate.getSubject()),
                certificate.getSubject().toString(),
                certificate.getIssuer().toString(),
                certificate.getSerialNumber().toString(16).toUpperCase(),
                toLocalDateTime(certificate.getNotBefore()),
                toLocalDateTime(certificate.getNotAfter()),
                algorithmName(signer),
                signingTime);
    }

    private byte[] decode(byte[] signatureFile) {
        String text = new String(signatureFile, StandardCharsets.US_ASCII).trim();
        if (text.startsWith(PEM_HEADER)) {
            return decodeBase64(stripPemArmour(text));
        }
        if (looksLikeBase64(text)) {
            return decodeBase64(text);
        }
        return signatureFile;
    }

    private String stripPemArmour(String text) {
        StringBuilder body = new StringBuilder();
        for (String line : text.split("\\r?\\n")) {
            if (!line.startsWith("-----")) {
                body.append(line.trim());
            }
        }
        return body.toString();
    }

    private boolean looksLikeBase64(String text) {
        if (text.isEmpty()) {
            return false;
        }
        for (int index = 0; index < text.length(); index++) {
            char symbol = text.charAt(index);
            boolean allowed = Character.isLetterOrDigit(symbol) && symbol < 128
                    || symbol == '+' || symbol == '/' || symbol == '='
                    || symbol == '\r' || symbol == '\n' || symbol == ' ';
            if (!allowed) {
                return false;
            }
        }
        return true;
    }

    private byte[] decodeBase64(String text) {
        try {
            return Base64.getMimeDecoder().decode(text);
        } catch (IllegalArgumentException ex) {
            throw new InvalidSignatureFileException("не удалось декодировать base64-содержимое");
        }
    }

    private CMSSignedData parse(byte[] container, byte[] documentContent) {
        try {
            return new CMSSignedData(new CMSProcessableByteArray(documentContent), container);
        } catch (CMSException ex) {
            log.debug("CMS parsing failed", ex);
            throw new InvalidSignatureFileException(
                    "ожидается открепленная подпись в формате CMS/PKCS#7 (.sig, .p7s)");
        }
    }

    private SignerInformation requireSingleSigner(CMSSignedData signedData) {
        Collection<SignerInformation> signers = signedData.getSignerInfos().getSigners();
        if (signers.isEmpty()) {
            throw new InvalidSignatureFileException("в контейнере нет подписей");
        }
        return signers.iterator().next();
    }

    private X509CertificateHolder findCertificate(CMSSignedData signedData, SignerInformation signer) {
        Store<X509CertificateHolder> certificates = signedData.getCertificates();
        Collection<X509CertificateHolder> matches = certificates.getMatches(signer.getSID());
        if (matches.isEmpty()) {
            throw new InvalidSignatureFileException("в контейнере нет сертификата подписанта");
        }
        return matches.iterator().next();
    }

    private void assertSignatureMatches(SignerInformation signer, X509CertificateHolder certificate) {
        try {
            boolean valid = signer.verify(new JcaSimpleSignerInfoVerifierBuilder()
                    .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                    .build(certificate));
            if (!valid) {
                throw new InvalidSignatureFileException("подпись не соответствует содержимому документа");
            }
        } catch (CMSVerifierCertificateNotValidException ex) {
            throw new InvalidSignatureFileException("сертификат был недействителен в момент подписания");
        } catch (CMSException | OperatorCreationException | CertificateException ex) {
            log.debug("CMS verification failed", ex);
            throw new InvalidSignatureFileException("подпись не соответствует содержимому документа");
        }
    }

    private void assertCertificateValid(X509CertificateHolder certificate, LocalDateTime signingTime) {
        Date moment = signingTime != null
                ? Date.from(signingTime.toInstant(ZoneOffset.UTC))
                : new Date();
        if (!certificate.isValidOn(moment)) {
            throw new InvalidSignatureFileException("сертификат недействителен на момент подписания");
        }
    }

    private LocalDateTime extractSigningTime(SignerInformation signer) {
        if (signer.getSignedAttributes() == null) {
            return null;
        }
        Attribute attribute = signer.getSignedAttributes().get(CMSAttributes.signingTime);
        if (attribute == null || attribute.getAttrValues().size() == 0) {
            return null;
        }
        Date signingTime = Time.getInstance(attribute.getAttrValues().getObjectAt(0)).getDate();
        return toLocalDateTime(signingTime);
    }

    private String algorithmName(SignerInformation signer) {
        String digest = ALGORITHM_NAMES.getAlgorithmName(new ASN1ObjectIdentifier(signer.getDigestAlgOID()));
        String encryption = ALGORITHM_NAMES.getAlgorithmName(new ASN1ObjectIdentifier(signer.getEncryptionAlgOID()));
        return digest + " / " + encryption;
    }

    private String commonName(X500Name subject) {
        RDN[] rdns = subject.getRDNs(BCStyle.CN);
        if (rdns.length == 0) {
            return null;
        }
        return IETFUtils.valueToString(rdns[0].getFirst().getValue());
    }

    private LocalDateTime toLocalDateTime(Date date) {
        return date == null ? null : LocalDateTime.ofInstant(date.toInstant(), ZoneOffset.UTC);
    }
}
