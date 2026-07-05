package com.pravoos.user.shared.util;

import com.pravoos.user.shared.exception.UndeliverableEmailException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.naming.NameNotFoundException;
import javax.naming.NamingException;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Component
public class EmailDeliverabilityValidator {

    private static final Logger log = LoggerFactory.getLogger(EmailDeliverabilityValidator.class);
    private static final String[] MAIL_RECORD_TYPES = {"MX", "A", "AAAA"};
    private static final Duration CACHE_TTL = Duration.ofHours(12);
    private static final int CACHE_MAX_SIZE = 10_000;

    private final Map<String, CacheEntry> deliverabilityCache = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) {
                    return size() > CACHE_MAX_SIZE;
                }
            });

    private record CacheEntry(boolean deliverable, Instant expiresAt) {
        boolean isFresh() {
            return expiresAt.isAfter(Instant.now());
        }
    }

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
        CacheEntry cached = deliverabilityCache.get(domain);
        if (cached != null && cached.isFresh()) {
            return cached.deliverable();
        }

        Boolean resolved = resolveMailRecords(domain);
        if (resolved == null) {
            return true;
        }

        cacheResult(domain, resolved);
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

    private void cacheResult(String domain, boolean deliverable) {
        deliverabilityCache.put(domain, new CacheEntry(deliverable, Instant.now().plus(CACHE_TTL)));
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
