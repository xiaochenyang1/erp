package com.tuowei.erp.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

@Component
@ConditionalOnProperty(prefix = "erp.security.principal-cache-invalidation", name = "mode", havingValue = "redis")
public class RedisPrincipalCacheInvalidationBus implements PrincipalCacheInvalidationBus, MessageListener {

    public static final String CHANNEL = "erp:security:principal-cache-invalidation";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final List<Consumer<PrincipalCacheInvalidationEvent>> listeners = new CopyOnWriteArrayList<>();

    public RedisPrincipalCacheInvalidationBus(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publish(PrincipalCacheInvalidationEvent event) {
        if (event == null) {
            return;
        }
        notifyLocalListeners(event);
        redisTemplate.convertAndSend(CHANNEL, serialize(event));
    }

    @Override
    public void subscribe(Consumer<PrincipalCacheInvalidationEvent> listener) {
        listeners.add(listener);
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        if (message == null || message.getBody() == null) {
            return;
        }
        notifyLocalListeners(deserialize(message.getBody()));
    }

    private void notifyLocalListeners(PrincipalCacheInvalidationEvent event) {
        listeners.forEach(listener -> listener.accept(event));
    }

    private String serialize(PrincipalCacheInvalidationEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (IOException ex) {
            throw new IllegalStateException("principal cache invalidation event serialization failed", ex);
        }
    }

    private PrincipalCacheInvalidationEvent deserialize(byte[] body) {
        try {
            return objectMapper.readValue(new String(body, StandardCharsets.UTF_8), PrincipalCacheInvalidationEvent.class);
        } catch (IOException ex) {
            throw new IllegalArgumentException("principal cache invalidation event payload is invalid", ex);
        }
    }
}
