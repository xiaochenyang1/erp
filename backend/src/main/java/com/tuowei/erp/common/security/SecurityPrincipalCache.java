package com.tuowei.erp.common.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

@Service
public class SecurityPrincipalCache {

    private final Clock clock;
    private final Duration ttl;
    private final PrincipalCacheInvalidationBus invalidationBus;
    private final ConcurrentMap<Long, CacheEntry> cache = new ConcurrentHashMap<>();

    @Autowired
    public SecurityPrincipalCache(
            Clock clock,
            @Value("${erp.security.principal-cache-ttl-seconds:60}") long ttlSeconds,
            PrincipalCacheInvalidationBus invalidationBus
    ) {
        this(clock, Duration.ofSeconds(ttlSeconds), invalidationBus);
    }

    SecurityPrincipalCache(Clock clock, Duration ttl) {
        this(clock, ttl, NoopPrincipalCacheInvalidationBus.INSTANCE);
    }

    SecurityPrincipalCache(Clock clock, Duration ttl, PrincipalCacheInvalidationBus invalidationBus) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.ttl = validateTtl(ttl);
        this.invalidationBus = Objects.requireNonNull(invalidationBus, "invalidationBus must not be null");
        this.invalidationBus.subscribe(this::handleInvalidation);
    }

    public ErpPrincipal get(Long userId, Supplier<ErpPrincipal> loader) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(loader, "loader must not be null");

        CacheEntry entry = cache.compute(userId, (key, current) -> {
            Instant now = clock.instant();
            if (current != null && current.expiresAt().isAfter(now)) {
                return current;
            }
            ErpPrincipal principal = Objects.requireNonNull(loader.get(), "principal loader returned null");
            return new CacheEntry(principal, now.plus(ttl));
        });
        return entry.principal();
    }

    public void evictUser(Long userId) {
        if (userId != null) {
            cache.remove(userId);
            invalidationBus.publish(PrincipalCacheInvalidationEvent.user(userId));
        }
    }

    public void evictAll() {
        cache.clear();
        invalidationBus.publish(PrincipalCacheInvalidationEvent.all());
    }

    private void handleInvalidation(PrincipalCacheInvalidationEvent event) {
        if (event == null) {
            return;
        }
        if (event.allUsers()) {
            cache.clear();
            return;
        }
        if (event.userId() != null) {
            cache.remove(event.userId());
        }
    }

    private static Duration validateTtl(Duration ttl) {
        Objects.requireNonNull(ttl, "ttl must not be null");
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("principal cache ttl must be positive");
        }
        return ttl;
    }

    private record CacheEntry(ErpPrincipal principal, Instant expiresAt) {
    }

    private enum NoopPrincipalCacheInvalidationBus implements PrincipalCacheInvalidationBus {
        INSTANCE;

        @Override
        public void publish(PrincipalCacheInvalidationEvent event) {
        }

        @Override
        public void subscribe(java.util.function.Consumer<PrincipalCacheInvalidationEvent> listener) {
        }
    }
}
