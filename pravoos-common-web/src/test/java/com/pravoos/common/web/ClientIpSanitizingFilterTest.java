package com.pravoos.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ClientIpSanitizingFilterTest {

  private static final String HEADER = "X-Client-Ip";

  private final ClientIpSanitizingFilter filter =
      new ClientIpSanitizingFilter(ClientIpSanitizingFilter.DEFAULT_TRUSTED_PEERS);

  @Test
  void keepsHeaderFromTrustedGateway() throws Exception {
    HttpServletRequest seen = filterWith("172.20.0.5", "203.0.113.7");

    assertThat(seen.getHeader(HEADER)).isEqualTo("203.0.113.7");
  }

  @Test
  void stripsSpoofedHeaderFromUntrustedPeer() throws Exception {
    HttpServletRequest seen = filterWith("203.0.113.9", "185.71.76.5");

    assertThat(seen.getHeader(HEADER)).isNull();
    assertThat(Collections.list(seen.getHeaders(HEADER))).isEmpty();
    assertThat(Collections.list(seen.getHeaderNames())).doesNotContain(HEADER);
  }

  @Test
  void headerLookupIgnoresCaseWhenStripping() throws Exception {
    HttpServletRequest seen = filterWith("203.0.113.9", "185.71.76.5");

    assertThat(seen.getHeader("x-client-ip")).isNull();
  }

  @Test
  void leavesOtherHeadersUntouched() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr("203.0.113.9");
    request.addHeader(HEADER, "185.71.76.5");
    request.addHeader("X-Request-Id", "abc-123");

    assertThat(filterThrough(request).getHeader("X-Request-Id")).isEqualTo("abc-123");
  }

  @Test
  void emptyTrustedListStripsHeaderEverywhere() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr("127.0.0.1");
    request.addHeader(HEADER, "185.71.76.5");

    MockFilterChain chain = new MockFilterChain();
    new ClientIpSanitizingFilter("").doFilter(request, new MockHttpServletResponse(), chain);

    assertThat(((HttpServletRequest) chain.getRequest()).getHeader(HEADER)).isNull();
  }

  @Test
  void defaultTrustedPeersCoverDockerAndLoopback() {
    assertThat(List.of("127.0.0.1", "10.1.2.3", "172.18.0.4", "192.168.1.9"))
        .allSatisfy(ip -> assertThat(filterKeepsHeaderFrom(ip)).isTrue());
    assertThat(filterKeepsHeaderFrom("203.0.113.9")).isFalse();
  }

  private boolean filterKeepsHeaderFrom(String remoteAddr) {
    try {
      return filterWith(remoteAddr, "203.0.113.7").getHeader(HEADER) != null;
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private HttpServletRequest filterWith(String remoteAddr, String clientIp) throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr(remoteAddr);
    request.addHeader(HEADER, clientIp);
    return filterThrough(request);
  }

  private HttpServletRequest filterThrough(MockHttpServletRequest request) throws Exception {
    MockFilterChain chain = new MockFilterChain();
    filter.doFilter(request, new MockHttpServletResponse(), chain);
    return (HttpServletRequest) chain.getRequest();
  }
}
