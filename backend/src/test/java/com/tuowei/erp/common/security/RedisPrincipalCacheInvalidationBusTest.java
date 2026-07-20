package com.tuowei.erp.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RedisPrincipalCacheInvalidationBusTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void publishSendsSerializedInvalidationEventToRedisChannel() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        RedisPrincipalCacheInvalidationBus bus = new RedisPrincipalCacheInvalidationBus(redisTemplate, objectMapper);

        bus.publish(PrincipalCacheInvalidationEvent.user(42L));

        verify(redisTemplate).convertAndSend(eq(RedisPrincipalCacheInvalidationBus.CHANNEL), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void redisMessageNotifiesLocalSubscribers() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        RedisPrincipalCacheInvalidationBus bus = new RedisPrincipalCacheInvalidationBus(redisTemplate, objectMapper);
        List<PrincipalCacheInvalidationEvent> events = new ArrayList<>();
        bus.subscribe(events::add);

        byte[] body = objectMapper.writeValueAsBytes(PrincipalCacheInvalidationEvent.all());
        Message message = new org.springframework.data.redis.connection.DefaultMessage(
                RedisPrincipalCacheInvalidationBus.CHANNEL.getBytes(StandardCharsets.UTF_8),
                body
        );

        bus.onMessage(message, RedisPrincipalCacheInvalidationBus.CHANNEL.getBytes(StandardCharsets.UTF_8));

        assertThat(events).containsExactly(PrincipalCacheInvalidationEvent.all());
    }
}
