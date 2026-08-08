package com.pravoos.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DeployPropertiesTest {

  @Test
  void hasServerIp_falseForNullOrBlank() {
    assertThat(new DeployProperties(null, "example.com").hasServerIp()).isFalse();
    assertThat(new DeployProperties("  ", "example.com").hasServerIp()).isFalse();
  }

  @Test
  void hasServerIp_trueWhenPresent() {
    assertThat(new DeployProperties("203.0.113.7", null).hasServerIp()).isTrue();
  }

  @Test
  void hasServerDomain_falseForNullOrBlank() {
    assertThat(new DeployProperties("203.0.113.7", null).hasServerDomain()).isFalse();
    assertThat(new DeployProperties("203.0.113.7", " ").hasServerDomain()).isFalse();
  }

  @Test
  void hasServerDomain_trueWhenPresent() {
    assertThat(new DeployProperties(null, "example.com").hasServerDomain()).isTrue();
  }
}
