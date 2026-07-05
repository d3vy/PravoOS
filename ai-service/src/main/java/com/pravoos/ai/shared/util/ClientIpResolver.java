package com.pravoos.ai.shared.util;

import jakarta.servlet.http.HttpServletRequest;

public final class ClientIpResolver {

    private static final String CLIENT_IP_HEADER = "X-Client-Ip";

    private ClientIpResolver() {
    }

    public static String resolve(HttpServletRequest request) {
        String clientIp = request.getHeader(CLIENT_IP_HEADER);
        if (clientIp != null && !clientIp.isBlank()) {
            return clientIp.trim();
        }
        return request.getRemoteAddr();
    }
}
