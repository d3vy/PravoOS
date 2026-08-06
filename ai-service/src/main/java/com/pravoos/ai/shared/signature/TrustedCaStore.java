package com.pravoos.ai.shared.signature;

import com.pravoos.ai.shared.config.SignatureProperties;
import com.pravoos.ai.shared.exception.TrustedCaStoreException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class TrustedCaStore {

  private static final Logger log = LoggerFactory.getLogger(TrustedCaStore.class);

  private final Set<TrustAnchor> anchors;

  public TrustedCaStore(SignatureProperties properties) {
    String location = properties.cms().trustedCaPath();
    if (location == null || location.isBlank()) {
      this.anchors = Set.of();
      log.info(
          "SIGNATURE_TRUSTED_CA_PATH не задан — цепочка сертификата УКЭП до аккредитованного УЦ не проверяется");
      return;
    }
    this.anchors = load(Path.of(location.trim()));
    log.info("Доверенных корневых сертификатов загружено: {} (из {})", anchors.size(), location);
  }

  public boolean configured() {
    return !anchors.isEmpty();
  }

  public Set<TrustAnchor> anchors() {
    return anchors;
  }

  private Set<TrustAnchor> load(Path location) {
    if (!Files.exists(location)) {
      throw new TrustedCaStoreException("путь с доверенными сертификатами не найден: " + location);
    }
    Set<TrustAnchor> loaded = new HashSet<>();
    for (Path file : certificateFiles(location)) {
      loaded.addAll(readAnchors(file));
    }
    if (loaded.isEmpty()) {
      throw new TrustedCaStoreException(
          "в " + location + " не найдено ни одного сертификата в формате PEM или DER");
    }
    return Set.copyOf(loaded);
  }

  private List<Path> certificateFiles(Path location) {
    if (!Files.isDirectory(location)) {
      return List.of(location);
    }
    try (Stream<Path> entries = Files.list(location)) {
      return entries.filter(Files::isRegularFile).sorted().toList();
    } catch (IOException ex) {
      throw new TrustedCaStoreException("не удалось прочитать каталог " + location, ex);
    }
  }

  private Set<TrustAnchor> readAnchors(Path file) {
    try (InputStream stream = Files.newInputStream(file)) {
      CertificateFactory factory = CertificateFactory.getInstance("X.509");
      Collection<? extends Certificate> certificates = factory.generateCertificates(stream);
      Set<TrustAnchor> fileAnchors = new HashSet<>();
      for (Certificate certificate : certificates) {
        if (certificate instanceof X509Certificate x509) {
          fileAnchors.add(new TrustAnchor(x509, null));
        }
      }
      return fileAnchors;
    } catch (CertificateException ex) {
      log.warn("Файл {} пропущен — не распознан как сертификат X.509", file);
      return Set.of();
    } catch (IOException ex) {
      throw new TrustedCaStoreException("не удалось прочитать файл " + file, ex);
    }
  }
}
