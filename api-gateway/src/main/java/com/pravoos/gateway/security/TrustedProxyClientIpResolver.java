package com.pravoos.gateway.security;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;

@Component
public class TrustedProxyClientIpResolver {

  private static final String X_FORWARDED_FOR = "X-Forwarded-For";
  private static final String UNKNOWN = "unknown";

  private final List<CidrRange> trustedProxies;

  public TrustedProxyClientIpResolver(
      @Value(
              "${app.gateway.trusted-proxies:127.0.0.1/32,::1/128,10.0.0.0/8,172.16.0.0/12,192.168.0.0/16}")
          String trustedProxiesCsv) {
    this.trustedProxies = parse(trustedProxiesCsv);
  }

  public String resolve(ServerHttpRequest request) {
    InetSocketAddress remoteAddress = request.getRemoteAddress();
    InetAddress remote = remoteAddress != null ? remoteAddress.getAddress() : null;
    if (remote == null) {
      return UNKNOWN;
    }
    if (!isTrusted(remote)) {
      return remote.getHostAddress();
    }
    return clientFromForwardedFor(request, remote);
  }

  private String clientFromForwardedFor(ServerHttpRequest request, InetAddress remote) {
    String forwardedFor = request.getHeaders().getFirst(X_FORWARDED_FOR);
    if (forwardedFor == null || forwardedFor.isBlank()) {
      return remote.getHostAddress();
    }
    String[] hops = forwardedFor.split(",");
    for (int i = hops.length - 1; i >= 0; i--) {
      String candidate = hops[i].trim();
      InetAddress candidateAddress = parseAddress(candidate);
      if (candidateAddress == null) {
        return remote.getHostAddress();
      }
      if (!isTrusted(candidateAddress)) {
        return candidate;
      }
    }
    return remote.getHostAddress();
  }

  private boolean isTrusted(InetAddress address) {
    for (CidrRange range : trustedProxies) {
      if (range.contains(address)) {
        return true;
      }
    }
    return false;
  }

  private List<CidrRange> parse(String csv) {
    List<CidrRange> ranges = new ArrayList<>();
    for (String entry : csv.split(",")) {
      String trimmed = entry.trim();
      if (!trimmed.isEmpty()) {
        ranges.add(CidrRange.of(trimmed));
      }
    }
    return ranges;
  }

  private static InetAddress parseAddress(String value) {
    try {
      return InetAddress.getByName(value);
    } catch (UnknownHostException e) {
      return null;
    }
  }

  private static final class CidrRange {

    private final byte[] network;
    private final int prefixBits;

    private CidrRange(byte[] network, int prefixBits) {
      this.network = network;
      this.prefixBits = prefixBits;
    }

    static CidrRange of(String cidr) {
      String[] parts = cidr.split("/");
      InetAddress address = parseAddress(parts[0]);
      if (address == null) {
        throw new IllegalArgumentException("Invalid trusted-proxy CIDR: " + cidr);
      }
      byte[] bytes = address.getAddress();
      int prefix = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : bytes.length * 8;
      return new CidrRange(bytes, prefix);
    }

    boolean contains(InetAddress address) {
      byte[] candidate = address.getAddress();
      if (candidate.length != network.length) {
        return false;
      }
      int fullBytes = prefixBits / 8;
      int remainingBits = prefixBits % 8;
      for (int i = 0; i < fullBytes; i++) {
        if (candidate[i] != network[i]) {
          return false;
        }
      }
      if (remainingBits == 0) {
        return true;
      }
      int mask = 0xFF << (8 - remainingBits);
      return (candidate[fullBytes] & mask) == (network[fullBytes] & mask);
    }
  }
}
