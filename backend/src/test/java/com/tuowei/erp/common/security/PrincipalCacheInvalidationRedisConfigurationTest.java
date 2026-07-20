package com.tuowei.erp.common.security;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PrincipalCacheInvalidationRedisConfigurationTest {

    @Test
    void redisInvalidationBusIsConfigurableAndLocalBusIsFallbackOnly() throws IOException {
        String localBus = readSource("LocalPrincipalCacheInvalidationBus.java");
        String redisBus = readSource("RedisPrincipalCacheInvalidationBus.java");
        String redisConfiguration = readSource("RedisPrincipalCacheInvalidationConfiguration.java");
        String prodProperties = Files.readString(Path.of("src", "main", "resources", "application-prod.yml"),
                StandardCharsets.UTF_8);

        assertThat(localBus)
                .contains("@ConditionalOnProperty(prefix = \"erp.security.principal-cache-invalidation\", name = \"mode\", havingValue = \"local\", matchIfMissing = true)");
        assertThat(redisBus)
                .contains("@ConditionalOnProperty(prefix = \"erp.security.principal-cache-invalidation\", name = \"mode\", havingValue = \"redis\")");
        assertThat(redisConfiguration)
                .contains("RedisMessageListenerContainer")
                .contains("RedisPrincipalCacheInvalidationBus.CHANNEL")
                .contains("container.addMessageListener(redisBus, new ChannelTopic");
        assertThat(prodProperties)
                .contains("principal-cache-invalidation:")
                .contains("mode: ${ERP_PRINCIPAL_CACHE_INVALIDATION_MODE:redis}");
    }

    private static String readSource(String fileName) throws IOException {
        return Files.readString(Path.of("src", "main", "java", "com", "tuowei", "erp",
                "common", "security", fileName), StandardCharsets.UTF_8);
    }
}
