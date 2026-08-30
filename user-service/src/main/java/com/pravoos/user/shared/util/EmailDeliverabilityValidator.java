package com.pravoos.user.shared.util;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.pravoos.user.shared.exception.UndeliverableEmailException;
import java.time.Duration;
import java.util.Hashtable;
import java.util.Locale;
import javax.naming.NameNotFoundException;
import javax.naming.NamingException;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class EmailDeliverabilityValidator {

  private static final Logger log = LoggerFactory.getLogger(EmailDeliverabilityValidator.class);
  private static final String[] MAIL_RECORD_TYPES = {"MX", "A", "AAAA"};
  private static final Duration CACHE_TTL = Duration.ofHours(12);
  private static final int CACHE_MAX_SIZE = 10_000;

  private final Cache<String, Boolean> deliverabilityCache =
      Caffeine.newBuilder().maximumSize(CACHE_MAX_SIZE).expireAfterWrite(CACHE_TTL).build();

  public void validate(String email) {
    int atIndex = email.lastIndexOf('@');
    if (atIndex < 0 || atIndex == email.length() - 1) {
      throw new UndeliverableEmailException();
    }
    String domain = email.substring(atIndex + 1).toLowerCase(Locale.ROOT);
    if (!isDeliverable(domain)) {
      throw new UndeliverableEmailException();
    }
  }

  private boolean isDeliverable(String domain) {
    Boolean cached = deliverabilityCache.getIfPresent(domain);
    if (cached != null) {
      return cached;
    }

    Boolean resolved = resolveMailRecords(domain);
    if (resolved == null) {
      return true;
    }

    deliverabilityCache.put(domain, resolved);
    return resolved;
  }

  private Boolean resolveMailRecords(String domain) {
    Hashtable<String, String> env = new Hashtable<>();
    env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
    env.put("com.sun.jndi.dns.timeout.initial", "2000");
    env.put("com.sun.jndi.dns.timeout.retries", "1");

    DirContext context = null;
    try {
      context = new InitialDirContext(env);
      Attributes attributes = context.getAttributes(domain, MAIL_RECORD_TYPES);
      return attributes.get("MX") != null
          || attributes.get("A") != null
          || attributes.get("AAAA") != null;
    } catch (NameNotFoundException e) {
      return false;
    } catch (NamingException e) {
      log.warn("DNS lookup failed for domain {}, accepting email: {}", domain, e.getMessage());
      return null;
    } finally {
      closeQuietly(context);
    }
  }

  private void closeQuietly(DirContext context) {
    if (context == null) {
      return;
    }
    try {
      context.close();
    } catch (NamingException ignored) {
    }
  }
}
