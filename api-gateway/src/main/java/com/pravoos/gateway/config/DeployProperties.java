package com.pravoos.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "deploy")
public record DeployProperties(String serverIp, String serverDomain) {

    public boolean hasServerIp() {
        return serverIp != null && !serverIp.isBlank();
    }

    public boolean hasServerDomain() {
        return serverDomain != null && !serverDomain.isBlank();
    }
}
