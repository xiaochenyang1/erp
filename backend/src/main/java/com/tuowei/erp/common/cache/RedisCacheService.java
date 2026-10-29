package com.tuowei.erp.common.cache;

import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

@Service
@Profile("!local & !test")
public class RedisCacheService implements CacheService {

    private static final long SCAN_COUNT = 1000;
    private static final int DELETE_BATCH_SIZE = 1000;

    private final StringRedisTemplate redisTemplate;

    public RedisCacheService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate must not be null");
    }

    @Override
    public String getOrLoad(String key, Duration ttl, Supplier<String> loader) {
        String safeKey = requireKey(key);
        Duration safeTtl = requireTtl(ttl);
        if (loader == null) {
            throw new IllegalArgumentException("cache loader must not be null");
        }

        String cached = redisTemplate.opsForValue().get(safeKey);
        if (cached != null) {
            return cached;
        }

        String loaded = loader.get();
        if (loaded != null) {
            redisTemplate.opsForValue().set(safeKey, loaded, safeTtl);
        }
        return loaded;
    }

    @Override
    public void evict(String key) {
        redisTemplate.delete(requireKey(key));
    }

    @Override
    public void evictByPrefix(String keyPrefix) {
        String safePrefix = requireKeyPrefix(keyPrefix);
        ScanOptions options = ScanOptions.scanOptions()
                .match(safePrefix + "*")
                .count(SCAN_COUNT)
                .build();
        Set<String> batch = new LinkedHashSet<>();
        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            if (cursor == null) {
                return;
            }
            while (cursor.hasNext()) {
                batch.add(cursor.next());
                if (batch.size() >= DELETE_BATCH_SIZE) {
                    deleteBatch(batch);
                }
            }
            deleteBatch(batch);
        }
    }

    private void deleteBatch(Set<String> batch) {
        if (!batch.isEmpty()) {
            redisTemplate.delete(Set.copyOf(batch));
            batch.clear();
        }
    }

    private String requireKey(String key) {
        if (!StringUtils.hasText(key)) {
            throw new IllegalArgumentException("cache key must not be blank");
        }
        return key.trim();
    }

    private String requireKeyPrefix(String keyPrefix) {
        if (!StringUtils.hasText(keyPrefix)) {
            throw new IllegalArgumentException("cache key prefix must not be blank");
        }
        String safePrefix = keyPrefix.trim();
        if (!safePrefix.endsWith(":")) {
            throw new IllegalArgumentException("cache key prefix must end with ':'");
        }
        return safePrefix;
    }

    private Duration requireTtl(Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("cache ttl must be positive");
        }
        return ttl;
    }
}
