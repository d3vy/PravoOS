package com.pravoos.user.util;

import jakarta.servlet.http.HttpServletRequest;

public final class ClientIpResolver {

    private static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";

    private ClientIpResolver() {
    }

    public static String resolve(HttpServletRequest request) {
        String forwardedFor = request.getHeader(FORWARDED_FOR_HEADER);
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            int commaIndex = forwardedFor.indexOf(',');
            String firstHop = commaIndex > 0 ? forwardedFor.substring(0, commaIndex) : forwardedFor;
            return firstHop.trim();
        }
        return request.getRemoteAddr();
    }
}
