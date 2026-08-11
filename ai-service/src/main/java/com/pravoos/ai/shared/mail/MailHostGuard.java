package com.pravoos.ai.shared.mail;

import com.pravoos.ai.shared.exception.MailboxHostNotAllowedException;
import com.pravoos.ai.shared.exception.MailboxPortNotAllowedException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class MailHostGuard {

  private static final Logger log = LoggerFactory.getLogger(MailHostGuard.class);
  private static final int MAX_HOST_LENGTH = 255;
  private static final Set<Integer> ALLOWED_IMAP_PORTS = Set.of(143, 993);

  public void requireAllowedPort(int port) {
    if (!ALLOWED_IMAP_PORTS.contains(port)) {
      log.warn("Отклонён IMAP-порт {}: разрешены только {}", port, ALLOWED_IMAP_PORTS);
      throw new MailboxPortNotAllowedException(port);
    }
  }

  public void requireRoutableHost(String host) {
    if (host == null || host.isBlank() || host.length() > MAX_HOST_LENGTH) {
      throw new MailboxHostNotAllowedException();
    }
    String normalized = host.trim().toLowerCase(Locale.ROOT);
    InetAddress[] resolved;
    try {
      resolved = InetAddress.getAllByName(normalized);
    } catch (UnknownHostException e) {
      log.warn("Отклонён IMAP-хост '{}': не резолвится", normalized);
      throw new MailboxHostNotAllowedException();
    }
    for (InetAddress address : resolved) {
      if (isInternal(address)) {
        log.warn("Отклонён IMAP-хост '{}': резолвится во внутренний адрес", normalized);
        throw new MailboxHostNotAllowedException();
      }
    }
  }

  private boolean isInternal(InetAddress address) {
    return address.isLoopbackAddress()
        || address.isAnyLocalAddress()
        || address.isLinkLocalAddress()
        || address.isSiteLocalAddress()
        || address.isMulticastAddress()
        || isUniqueLocalIpv6(address)
        || isSharedAddressSpace(address);
  }

  private boolean isUniqueLocalIpv6(InetAddress address) {
    byte[] bytes = address.getAddress();
    return bytes.length == 16 && (bytes[0] & 0xFE) == 0xFC;
  }

  private boolean isSharedAddressSpace(InetAddress address) {
    byte[] bytes = address.getAddress();
    return bytes.length == 4 && (bytes[0] & 0xFF) == 100 && (bytes[1] & 0xC0) == 64;
  }
}
