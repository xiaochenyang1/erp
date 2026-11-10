package com.tuowei.erp.common.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Service
@Profile({"local", "test"})
public class LocalCacheService implements CacheService {

    private static final Logger log = LoggerFactory.getLogger(LocalCacheService.class);
    private final Clock clock;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public LocalCacheService(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        log.info("当前 profile 使用本地内存缓存");
    }

    @Override
    public String getOrLoad(String key, Duration ttl, Supplier<String> loader) {
        String safeKey = requireKey(key);
        Duration safeTtl = requireTtl(ttl);
        if (loader == null) {
            throw new IllegalArgumentException("cache loader must not be null");
        }

        Instant now = clock.instant();
        CacheEntry entry = cache.compute(safeKey, (k, current) -> {
            if (current != null && current.expiresAt().isAfter(now)) {
                return current;
            }
            String loaded = loader.get();
            if (loaded == null) {
                return null;
            }
            return new CacheEntry(loaded, now.plus(safeTtl));
        });

        return entry != null ? entry.value() : null;
    }

    @Override
    public void evict(String key) {
        cache.remove(requireKey(key));
    }

    @Override
    public void evictByPrefix(String keyPrefix) {
        String safePrefix = requireKeyPrefix(keyPrefix);
        cache.keySet().removeIf(key -> key.startsWith(safePrefix));
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

    private record CacheEntry(String value, Instant expiresAt) {
    }
}
