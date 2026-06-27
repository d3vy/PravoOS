package com.pravoos.common.config;

public final class RedisPasswordCheck {

    private RedisPasswordCheck() {
    }

    public static void requirePassword(String redisPassword, String reason) {
        if (redisPassword == null || redisPassword.isBlank()) {
            throw new IllegalStateException(
                    "REDIS_PASSWORD must be set in production (profile 'docker'). " + reason);
        }
    }
}
