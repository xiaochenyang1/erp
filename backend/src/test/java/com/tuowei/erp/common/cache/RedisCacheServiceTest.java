package com.tuowei.erp.common.cache;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class RedisCacheServiceTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> valueOperations = mockValueOperations();
    private final Cursor<String> cursor = mockCursor();
    private final RedisCacheService cacheService = new RedisCacheService(redisTemplate);

    @Test
    void returnsCachedValueWithoutCallingLoader() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("erp:1:2:dict:items:status")).thenReturn("cached");

        String value = cacheService.getOrLoad(
                "erp:1:2:dict:items:status",
                Duration.ofMinutes(5),
                () -> "loaded"
        );

        assertThat(value).isEqualTo("cached");
        verify(valueOperations).get("erp:1:2:dict:items:status");
        verifyNoMoreInteractions(valueOperations);
    }

    @Test
    void loadsAndStoresValueWhenMissing() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("erp:1:2:dict:items:status")).thenReturn(null);
        AtomicInteger loads = new AtomicInteger();

        String value = cacheService.getOrLoad(
                "erp:1:2:dict:items:status",
                Duration.ofMinutes(5),
                () -> "loaded-" + loads.incrementAndGet()
        );

        assertThat(value).isEqualTo("loaded-1");
        assertThat(loads).hasValue(1);
        verify(valueOperations).set("erp:1:2:dict:items:status", "loaded-1", Duration.ofMinutes(5));
    }

    @Test
    void doesNotCacheNullLoaderResult() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("erp:1:2:dict:items:status")).thenReturn(null);

        String value = cacheService.getOrLoad(
                "erp:1:2:dict:items:status",
                Duration.ofMinutes(5),
                () -> null
        );

        assertThat(value).isNull();
        verify(valueOperations).get("erp:1:2:dict:items:status");
        verifyNoMoreInteractions(valueOperations);
    }

    @Test
    void evictsKey() {
        cacheService.evict("erp:1:2:dict:items:status");

        verify(redisTemplate).delete("erp:1:2:dict:items:status");
    }

    @Test
    void evictsKeysByPrefixUsingScanInsteadOfKeys() {
        when(redisTemplate.scan(any(ScanOptions.class))).thenReturn(cursor);
        when(cursor.hasNext()).thenReturn(true, true, false);
        when(cursor.next()).thenReturn(
                "erp:1:2:permission:user:7",
                "erp:1:2:permission:user:8"
        );

        cacheService.evictByPrefix("erp:1:2:permission:");

        ArgumentCaptor<ScanOptions> options = ArgumentCaptor.forClass(ScanOptions.class);
        verify(redisTemplate).scan(options.capture());
        assertThat(options.getValue().getPattern()).isEqualTo("erp:1:2:permission:*");
        assertThat(options.getValue().getCount()).isEqualTo(1000L);
        verify(redisTemplate).delete(Set.of(
                "erp:1:2:permission:user:7",
                "erp:1:2:permission:user:8"
        ));
        verify(redisTemplate, never()).keys(anyString());
        verify(cursor).close();
    }

    @Test
    void rejectsInvalidInputs() {
        assertThatThrownBy(() -> cacheService.getOrLoad("", Duration.ofMinutes(5), () -> "value"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("cache key must not be blank");

        assertThatThrownBy(() -> cacheService.getOrLoad("erp:1:2:key", Duration.ZERO, () -> "value"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("cache ttl must be positive");

        assertThatThrownBy(() -> cacheService.getOrLoad("erp:1:2:key", Duration.ofMinutes(5), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("cache loader must not be null");

        assertThatThrownBy(() -> cacheService.evictByPrefix(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("cache key prefix must not be blank");

        assertThatThrownBy(() -> cacheService.evictByPrefix("erp:1:2:permission"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("cache key prefix must end with ':'");
    }

    @SuppressWarnings("unchecked")
    private static ValueOperations<String, String> mockValueOperations() {
        return mock(ValueOperations.class);
    }

    @SuppressWarnings("unchecked")
    private static Cursor<String> mockCursor() {
        return mock(Cursor.class);
    }
}
