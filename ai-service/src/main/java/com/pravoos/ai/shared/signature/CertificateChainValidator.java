package com.pravoos.ai.shared.signature;

import com.pravoos.ai.shared.exception.InvalidSignatureFileException;
import com.pravoos.ai.shared.exception.TrustedCaStoreException;
import java.security.GeneralSecurityException;
import java.security.cert.CertPathBuilder;
import java.security.cert.CertPathBuilderException;
import java.security.cert.CertStore;
import java.security.cert.CertificateException;
import java.security.cert.CollectionCertStoreParameters;
import java.security.cert.PKIXBuilderParameters;
import java.security.cert.X509CertSelector;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class CertificateChainValidator {

  private static final Logger log = LoggerFactory.getLogger(CertificateChainValidator.class);

  private final TrustedCaStore trustedCaStore;

  public CertificateChainValidator(TrustedCaStore trustedCaStore) {
    this.trustedCaStore = trustedCaStore;
  }

  public boolean validate(
      X509CertificateHolder signerCertificate,
      List<X509CertificateHolder> containerCertificates,
      LocalDateTime signingTime) {
    if (!trustedCaStore.configured()) {
      return false;
    }
    List<X509Certificate> chain = convert(containerCertificates);
    X509Certificate signer = convert(List.of(signerCertificate)).get(0);
    Date moment =
        signingTime != null ? Date.from(signingTime.toInstant(ZoneOffset.UTC)) : new Date();
    try {
      X509CertSelector target = new X509CertSelector();
      target.setCertificate(signer);

      PKIXBuilderParameters parameters =
          new PKIXBuilderParameters(trustedCaStore.anchors(), target);
      parameters.setRevocationEnabled(false);
      parameters.setDate(moment);
      parameters.addCertStore(
          CertStore.getInstance(
              "Collection",
              new CollectionCertStoreParameters(chain),
              BouncyCastleProvider.PROVIDER_NAME));

      CertPathBuilder.getInstance("PKIX", BouncyCastleProvider.PROVIDER_NAME).build(parameters);
      return true;
    } catch (CertPathBuilderException ex) {
      log.debug("Certificate chain validation failed", ex);
      throw new InvalidSignatureFileException(
          "сертификат подписанта не выстраивается в цепочку до аккредитованного УЦ "
              + "из доверенного списка");
    } catch (GeneralSecurityException ex) {
      throw new TrustedCaStoreException("проверка цепочки недоступна", ex);
    }
  }

  private List<X509Certificate> convert(List<X509CertificateHolder> holders) {
    JcaX509CertificateConverter converter =
        new JcaX509CertificateConverter().setProvider(BouncyCastleProvider.PROVIDER_NAME);
    List<X509Certificate> certificates = new ArrayList<>(holders.size());
    for (X509CertificateHolder holder : holders) {
      try {
        certificates.add(converter.getCertificate(holder));
      } catch (CertificateException ex) {
        throw new InvalidSignatureFileException("сертификат в контейнере повреждён");
      }
    }
    return certificates;
  }
}
