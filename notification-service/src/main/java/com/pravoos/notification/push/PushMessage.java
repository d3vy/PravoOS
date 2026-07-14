package com.pravoos.notification.push;

public record PushMessage(
        String title,
        String body,
        String url,
        String tag,
        int ttlSeconds
) {
    private static final int DEFAULT_TTL_SECONDS = 24 * 60 * 60;

    public static PushMessage of(String title, String body, String url, String tag) {
        return new PushMessage(title, body, url, tag, DEFAULT_TTL_SECONDS);
    }
}
