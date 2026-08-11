package com.pravoos.common.web;

import com.pravoos.common.security.CidrRanges;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class ClientIpSanitizingFilter extends OncePerRequestFilter {

  public static final String DEFAULT_TRUSTED_PEERS =
      "127.0.0.1/32,::1/128,10.0.0.0/8,172.16.0.0/12,192.168.0.0/16";

  private static final Logger log = LoggerFactory.getLogger(ClientIpSanitizingFilter.class);
  private static final String CLIENT_IP_HEADER = "X-Client-Ip";

  private final CidrRanges trustedPeers;

  public ClientIpSanitizingFilter(String trustedPeersCsv) {
    this.trustedPeers = CidrRanges.parse(trustedPeersCsv);
    if (this.trustedPeers.isEmpty()) {
      log.warn(
          "Список доверенных источников {} пуст — заголовок отбрасывается у всех вызовов",
          CLIENT_IP_HEADER);
    }
  }

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain filterChain)
      throws ServletException, IOException {
    if (request.getHeader(CLIENT_IP_HEADER) == null
        || trustedPeers.contains(request.getRemoteAddr())) {
      filterChain.doFilter(request, response);
      return;
    }
    log.warn(
        "Заголовок {} от недоверенного источника {} отброшен",
        CLIENT_IP_HEADER,
        request.getRemoteAddr());
    filterChain.doFilter(new ClientIpStrippingRequest(request), response);
  }

  private static final class ClientIpStrippingRequest extends HttpServletRequestWrapper {

    private ClientIpStrippingRequest(HttpServletRequest request) {
      super(request);
    }

    @Override
    public String getHeader(String name) {
      return isClientIpHeader(name) ? null : super.getHeader(name);
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
      return isClientIpHeader(name) ? Collections.emptyEnumeration() : super.getHeaders(name);
    }

    @Override
    public Enumeration<String> getHeaderNames() {
      List<String> names = new ArrayList<>();
      Enumeration<String> original = super.getHeaderNames();
      while (original.hasMoreElements()) {
        String name = original.nextElement();
        if (!isClientIpHeader(name)) {
          names.add(name);
        }
      }
      return Collections.enumeration(names);
    }

    private boolean isClientIpHeader(String name) {
      return CLIENT_IP_HEADER.equalsIgnoreCase(name);
    }
  }
}
