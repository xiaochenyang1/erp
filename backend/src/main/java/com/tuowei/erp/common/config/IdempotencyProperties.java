package com.tuowei.erp.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "erp.idempotency")
public record IdempotencyProperties(
        boolean enabled,
        long ttlSeconds,
        int maxReplayBodyBytes,
        int maxRequestBodyBytes
) {

    public IdempotencyProperties {
        if (ttlSeconds < 1) {
            ttlSeconds = 24 * 60 * 60;
        }
        if (maxReplayBodyBytes < 1) {
            maxReplayBodyBytes = 1024 * 1024;
        }
        if (maxRequestBodyBytes < 1) {
            maxRequestBodyBytes = 1024 * 1024;
        }
    }
}
