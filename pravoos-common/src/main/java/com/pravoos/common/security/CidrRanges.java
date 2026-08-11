package com.pravoos.common.security;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

public final class CidrRanges {

  private final List<CidrRange> ranges;

  private CidrRanges(List<CidrRange> ranges) {
    this.ranges = ranges;
  }

  public static CidrRanges parse(String csv) {
    List<CidrRange> parsed = new ArrayList<>();
    if (csv != null) {
      for (String entry : csv.split(",")) {
        String trimmed = entry.trim();
        if (!trimmed.isEmpty()) {
          CidrRange range = CidrRange.parse(trimmed);
          if (range != null) {
            parsed.add(range);
          }
        }
      }
    }
    return new CidrRanges(List.copyOf(parsed));
  }

  public boolean isEmpty() {
    return ranges.isEmpty();
  }

  public boolean contains(String address) {
    byte[] candidate = toAddress(address);
    if (candidate == null) {
      return false;
    }
    return ranges.stream().anyMatch(range -> range.contains(candidate));
  }

  private static byte[] toAddress(String address) {
    if (address == null || address.isBlank()) {
      return null;
    }
    try {
      return InetAddress.getByName(address.trim()).getAddress();
    } catch (UnknownHostException e) {
      return null;
    }
  }

  private record CidrRange(byte[] network, int prefixLength) {

    static CidrRange parse(String cidr) {
      String[] parts = cidr.split("/");
      byte[] network = toAddress(parts[0]);
      if (network == null) {
        return null;
      }
      int prefixLength = network.length * 8;
      if (parts.length == 2) {
        try {
          prefixLength = Integer.parseInt(parts[1].trim());
        } catch (NumberFormatException e) {
          return null;
        }
      }
      if (prefixLength < 0 || prefixLength > network.length * 8) {
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
