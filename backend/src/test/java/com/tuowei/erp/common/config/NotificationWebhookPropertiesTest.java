package com.tuowei.erp.common.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationWebhookPropertiesTest {

    @Test
    void invalidLimitsFallBackToBoundedDefaults() {
        NotificationWebhookProperties properties = new NotificationWebhookProperties(
                true,
                0,
                0,
                Duration.ZERO,
                null,
                false
        );

        assertThat(properties.poolSize()).isEqualTo(NotificationWebhookProperties.DEFAULT_POOL_SIZE);
        assertThat(properties.queueCapacity()).isEqualTo(NotificationWebhookProperties.DEFAULT_QUEUE_CAPACITY);
        assertThat(properties.requestTimeout()).isEqualTo(NotificationWebhookProperties.DEFAULT_REQUEST_TIMEOUT);
        assertThat(properties.allowedHosts()).isEmpty();
    }

    @Test
    void defaultsKeepWebhookDisabledOnlyByMissingUrlAndRejectPrivateAddresses() {
        NotificationWebhookProperties properties = NotificationWebhookProperties.defaults();

        assertThat(properties.enabled()).isTrue();
        assertThat(properties.allowPrivateAddresses()).isFalse();
    }
}
