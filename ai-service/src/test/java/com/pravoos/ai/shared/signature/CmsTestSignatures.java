package com.pravoos.ai.shared.signature;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;

public final class CmsTestSignatures {

  static {
    if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
      Security.addProvider(new BouncyCastleProvider());
    }
  }

  private CmsTestSignatures() {}

  public record IssuedSignature(byte[] container, String caCertificatePem) {}

  public static IssuedSignature signatureIssuedByCa(byte[] content, String commonName) {
    try {
      KeyPair caKeyPair = generateKeyPair();
      Date notBefore = Date.from(Instant.now().minus(1, ChronoUnit.HOURS));
      Date notAfter = Date.from(Instant.now().plus(365, ChronoUnit.DAYS));

      X500Name caName = new X500Name("CN=Тестовый аккредитованный УЦ, O=PravoOS, C=RU");
      ContentSigner caSigner = contentSigner(caKeyPair);
      X509CertificateHolder caHolder =
          new JcaX509v3CertificateBuilder(
                  caName,
                  BigInteger.valueOf(System.nanoTime()),
                  notBefore,
                  notAfter,
                  caName,
                  caKeyPair.getPublic())
              .addExtension(Extension.basicConstraints, true, new BasicConstraints(0))
              .addExtension(
                  Extension.keyUsage, true, new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign))
              .build(caSigner);

      KeyPair signerKeyPair = generateKeyPair();
      X500Name subject = new X500Name("CN=" + commonName + ", O=Клиент, C=RU");
      X509CertificateHolder signerHolder =
          new JcaX509v3CertificateBuilder(
                  caName,
                  BigInteger.valueOf(System.nanoTime()),
                  notBefore,
                  notAfter,
                  subject,
                  signerKeyPair.getPublic())
              .addExtension(Extension.basicConstraints, true, new BasicConstraints(false))
              .addExtension(
                  Extension.keyUsage,
                  true,
                  new KeyUsage(KeyUsage.digitalSignature | KeyUsage.nonRepudiation))
              .build(caSigner);

      X509Certificate signerCertificate = toCertificate(signerHolder);
      CMSSignedDataGenerator generator = new CMSSignedDataGenerator();
      generator.addSignerInfoGenerator(
          new JcaSignerInfoGeneratorBuilder(
                  new JcaDigestCalculatorProviderBuilder()
                      .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                      .build())
              .build(contentSigner(signerKeyPair), signerCertificate));
      generator.addCertificates(new JcaCertStore(List.of(signerCertificate)));

      CMSSignedData signedData = generator.generate(new CMSProcessableByteArray(content), false);
      return new IssuedSignature(signedData.getEncoded(), toPem(toCertificate(caHolder)));
    } catch (Exception ex) {
      throw new IllegalStateException("Failed to build CA-issued test CMS signature", ex);
    }
  }

  private static String toPem(X509Certificate certificate) throws Exception {
    return "-----BEGIN CERTIFICATE-----\n"
        + Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(certificate.getEncoded())
        + "\n-----END CERTIFICATE-----\n";
  }

  private static X509Certificate toCertificate(X509CertificateHolder holder) throws Exception {
    return new JcaX509CertificateConverter()
        .setProvider(BouncyCastleProvider.PROVIDER_NAME)
        .getCertificate(holder);
  }

  private static ContentSigner contentSigner(KeyPair keyPair) throws Exception {
    return new JcaContentSignerBuilder("SHA256withRSA")
        .setProvider(BouncyCastleProvider.PROVIDER_NAME)
        .build(keyPair.getPrivate());
  }

  private static KeyPair generateKeyPair() throws Exception {
    KeyPairGenerator keyPairGenerator =
        KeyPairGenerator.getInstance("RSA", BouncyCastleProvider.PROVIDER_NAME);
    keyPairGenerator.initialize(2048);
    return keyPairGenerator.generateKeyPair();
  }

  public static byte[] detachedSignature(byte[] content, String commonName) {
    return detachedSignature(
        content,
        commonName,
        Date.from(Instant.now().minus(1, ChronoUnit.HOURS)),
        Date.from(Instant.now().plus(365, ChronoUnit.DAYS)));
  }

  public static byte[] detachedSignature(
      byte[] content, String commonName, Date notBefore, Date notAfter) {
    try {
      KeyPairGenerator keyPairGenerator =
          KeyPairGenerator.getInstance("RSA", BouncyCastleProvider.PROVIDER_NAME);
      keyPairGenerator.initialize(2048);
      KeyPair keyPair = keyPairGenerator.generateKeyPair();

      X500Name subject = new X500Name("CN=" + commonName + ", O=Тестовый УЦ, C=RU");
      ContentSigner certSigner =
          new JcaContentSignerBuilder("SHA256withRSA")
              .setProvider(BouncyCastleProvider.PROVIDER_NAME)
              .build(keyPair.getPrivate());
      X509CertificateHolder certificateHolder =
          new JcaX509v3CertificateBuilder(
                  subject,
                  BigInteger.valueOf(System.nanoTime()),
                  notBefore,
                  notAfter,
                  subject,
                  keyPair.getPublic())
              .build(certSigner);
      X509Certificate certificate =
          new JcaX509CertificateConverter()
              .setProvider(BouncyCastleProvider.PROVIDER_NAME)
              .getCertificate(certificateHolder);

      CMSSignedDataGenerator generator = new CMSSignedDataGenerator();
      generator.addSignerInfoGenerator(
          new JcaSignerInfoGeneratorBuilder(
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
