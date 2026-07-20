package com.tuowei.erp;

import org.mockito.Mockito;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@Configuration
@Profile("test")
public class TestRedisConfiguration {

    @Bean
    @Primary
    @SuppressWarnings("unchecked")
    public StringRedisTemplate testStringRedisTemplate() {
        Map<String, String> store = new ConcurrentHashMap<>();
        StringRedisTemplate redisTemplate = Mockito.mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = Mockito.mock(ValueOperations.class);

        when(redisTemplate.hasKey(anyString())).thenAnswer(invocation ->
                store.containsKey(invocation.getArgument(0, String.class)));
        when(redisTemplate.delete(any(Collection.class))).thenAnswer(invocation -> {
            Collection<String> keys = invocation.getArgument(0);
            long removed = 0;
            for (String key : keys) {
                if (store.remove(key) != null) {
                    removed++;
                }
            }
            return removed;
        });
        when(redisTemplate.expire(anyString(), any(Duration.class))).thenReturn(true);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0, String.class);
            return Long.parseLong(store.merge(key, "1", (oldValue, ignored) ->
                    String.valueOf(Long.parseLong(oldValue) + 1L)));
        });
        doAnswer(invocation -> {
            store.put(invocation.getArgument(0, String.class), invocation.getArgument(1, String.class));
            return null;
        }).when(valueOperations).set(anyString(), anyString(), any(Duration.class));

        return redisTemplate;
    }
}
