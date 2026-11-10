package com.tuowei.erp.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Runtime limits and network policy for best-effort notification webhooks. */
@ConfigurationProperties(prefix = "erp.notification.webhook")
public record NotificationWebhookProperties(
        boolean enabled,
        int poolSize,
        int queueCapacity,
        Duration requestTimeout,
        String allowedHosts,
        boolean allowPrivateAddresses
) {

    public static final int DEFAULT_POOL_SIZE = 2;
    public static final int DEFAULT_QUEUE_CAPACITY = 500;
    public static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(5);

    public NotificationWebhookProperties {
        if (poolSize < 1) {
            poolSize = DEFAULT_POOL_SIZE;
        }
        if (queueCapacity < 1) {
            queueCapacity = DEFAULT_QUEUE_CAPACITY;
        }
        if (requestTimeout == null || requestTimeout.isZero() || requestTimeout.isNegative()) {
            requestTimeout = DEFAULT_REQUEST_TIMEOUT;
        }
        allowedHosts = allowedHosts == null ? "" : allowedHosts.trim();
    }

    public static NotificationWebhookProperties defaults() {
        return new NotificationWebhookProperties(
                true,
                DEFAULT_POOL_SIZE,
                DEFAULT_QUEUE_CAPACITY,
                DEFAULT_REQUEST_TIMEOUT,
                "",
                false
        );
    }
}
