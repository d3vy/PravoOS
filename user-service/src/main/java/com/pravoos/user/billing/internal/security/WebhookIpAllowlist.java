package com.pravoos.user.billing.internal.security;

import com.pravoos.user.billing.internal.config.YooKassaProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

@Component
public class WebhookIpAllowlist {

    private static final Logger log = LoggerFactory.getLogger(WebhookIpAllowlist.class);
    private static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";

    private final List<CidrRange> allowedRanges;

    public WebhookIpAllowlist(YooKassaProperties properties) {
        this.allowedRanges = properties.webhookAllowedIps().stream()
                .map(CidrRange::parse)
                .filter(range -> range != null)
                .toList();
        if (allowedRanges.isEmpty()) {
            log.warn("YooKassa webhook IP allowlist is empty — webhook callers are not restricted by IP");
        }
    }

    public boolean permits(HttpServletRequest request) {
        if (allowedRanges.isEmpty()) {
            return true;
        }
        String clientIp = resolveClientIp(request);
        byte[] address = toAddress(clientIp);
        if (address == null) {
            log.warn("Webhook rejected: unparseable client IP {}", clientIp);
            return false;
        }
        boolean allowed = allowedRanges.stream().anyMatch(range -> range.contains(address));
        if (!allowed) {
            log.warn("Webhook rejected: client IP {} is not in the YooKassa allowlist", clientIp);
        }
        return allowed;
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader(FORWARDED_FOR_HEADER);
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static byte[] toAddress(String ip) {
        if (ip == null || ip.isBlank()) {
            return null;
        }
        try {
            return InetAddress.getByName(ip).getAddress();
        } catch (UnknownHostException ex) {
            return null;
        }
    }

    private record CidrRange(byte[] network, int prefixLength) {

        static CidrRange parse(String cidr) {
            String[] parts = cidr.trim().split("/");
            byte[] network = toAddress(parts[0]);
            if (network == null) {
                log.warn("Ignoring malformed webhook allowlist entry: {}", cidr);
                return null;
            }
            int prefixLength = network.length * 8;
            if (parts.length == 2) {
                try {
                    prefixLength = Integer.parseInt(parts[1].trim());
                } catch (NumberFormatException ex) {
                    log.warn("Ignoring malformed webhook allowlist entry: {}", cidr);
                    return null;
                }
            }
            if (prefixLength < 0 || prefixLength > network.length * 8) {
                log.warn("Ignoring malformed webhook allowlist entry: {}", cidr);
                return null;
            }
            return new CidrRange(network, prefixLength);
        }

        boolean contains(byte[] address) {
            if (address.length != network.length) {
                return false;
            }
            int fullBytes = prefixLength / 8;
            for (int i = 0; i < fullBytes; i++) {
                if (address[i] != network[i]) {
                    return false;
                }
            }
            int remainingBits = prefixLength % 8;
            if (remainingBits == 0) {
                return true;
            }
            int mask = 0xFF << (8 - remainingBits);
            return (address[fullBytes] & mask) == (network[fullBytes] & mask);
        }
    }
}
