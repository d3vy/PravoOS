package com.pravoos.ai.shared.signature;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;

public final class CmsTestSignatures {

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private CmsTestSignatures() {
    }

    public static byte[] detachedSignature(byte[] content, String commonName) {
        return detachedSignature(content, commonName,
                Date.from(Instant.now().minus(1, ChronoUnit.HOURS)),
                Date.from(Instant.now().plus(365, ChronoUnit.DAYS)));
    }

    public static byte[] detachedSignature(byte[] content, String commonName, Date notBefore, Date notAfter) {
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA", BouncyCastleProvider.PROVIDER_NAME);
            keyPairGenerator.initialize(2048);
            KeyPair keyPair = keyPairGenerator.generateKeyPair();

            X500Name subject = new X500Name("CN=" + commonName + ", O=Тестовый УЦ, C=RU");
            ContentSigner certSigner = new JcaContentSignerBuilder("SHA256withRSA")
                    .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                    .build(keyPair.getPrivate());
            X509CertificateHolder certificateHolder = new JcaX509v3CertificateBuilder(
                    subject,
                    BigInteger.valueOf(System.nanoTime()),
                    notBefore,
                    notAfter,
                    subject,
                    keyPair.getPublic()).build(certSigner);
            X509Certificate certificate = new JcaX509CertificateConverter()
                    .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                    .getCertificate(certificateHolder);

            CMSSignedDataGenerator generator = new CMSSignedDataGenerator();
            generator.addSignerInfoGenerator(new JcaSignerInfoGeneratorBuilder(
                    new JcaDigestCalculatorProviderBuilder()
                            .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                            .build())
                    .build(certSigner, certificate));
            generator.addCertificates(new JcaCertStore(List.of(certificate)));

            CMSSignedData signedData = generator.generate(new CMSProcessableByteArray(content), false);
            return signedData.getEncoded();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to build test CMS signature", ex);
        }
    }
}
