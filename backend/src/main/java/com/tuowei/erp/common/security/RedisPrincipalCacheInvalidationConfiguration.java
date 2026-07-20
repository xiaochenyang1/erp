package com.tuowei.erp.common.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
@ConditionalOnBean(RedisPrincipalCacheInvalidationBus.class)
public class RedisPrincipalCacheInvalidationConfiguration {

    @Bean
    RedisMessageListenerContainer principalCacheInvalidationRedisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            RedisPrincipalCacheInvalidationBus redisBus
    ) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(redisBus, new ChannelTopic(RedisPrincipalCacheInvalidationBus.CHANNEL));
        return container;
    }
}
