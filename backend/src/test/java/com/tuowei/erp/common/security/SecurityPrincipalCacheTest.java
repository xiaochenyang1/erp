package com.tuowei.erp.common.security;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityPrincipalCacheTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-05-29T00:00:00Z"));

    @Test
    void returnsCachedPrincipalWithinTtlAndReloadsAfterUserEviction() {
        SecurityPrincipalCache cache = new SecurityPrincipalCache(clock, Duration.ofMinutes(5));
        AtomicInteger loads = new AtomicInteger();

        ErpPrincipal first = cache.get(7L, () -> principal(7L, "user:read:" + loads.incrementAndGet()));
        ErpPrincipal second = cache.get(7L, () -> principal(7L, "user:read:" + loads.incrementAndGet()));

        assertThat(second).isSameAs(first);
        assertThat(loads).hasValue(1);

        cache.evictUser(7L);
        ErpPrincipal reloaded = cache.get(7L, () -> principal(7L, "user:read:" + loads.incrementAndGet()));

        assertThat(reloaded).isNotSameAs(first);
        assertThat(reloaded.permissions()).containsExactly("user:read:2");
        assertThat(loads).hasValue(2);
    }

    @Test
    void reloadsPrincipalAfterTtlExpires() {
        SecurityPrincipalCache cache = new SecurityPrincipalCache(clock, Duration.ofSeconds(30));
        AtomicInteger loads = new AtomicInteger();

        ErpPrincipal first = cache.get(8L, () -> principal(8L, "scope:" + loads.incrementAndGet()));
        clock.advance(Duration.ofSeconds(31));
        ErpPrincipal expired = cache.get(8L, () -> principal(8L, "scope:" + loads.incrementAndGet()));

        assertThat(expired).isNotSameAs(first);
        assertThat(expired.permissions()).containsExactly("scope:2");
        assertThat(loads).hasValue(2);
    }

    @Test
    void evictAllClearsEveryCachedPrincipal() {
        SecurityPrincipalCache cache = new SecurityPrincipalCache(clock, Duration.ofMinutes(5));
        AtomicInteger loads = new AtomicInteger();

        cache.get(9L, () -> principal(9L, "perm:" + loads.incrementAndGet()));
        cache.get(10L, () -> principal(10L, "perm:" + loads.incrementAndGet()));
        cache.evictAll();
        cache.get(9L, () -> principal(9L, "perm:" + loads.incrementAndGet()));
        cache.get(10L, () -> principal(10L, "perm:" + loads.incrementAndGet()));

        assertThat(loads).hasValue(4);
    }

    @Test
    void userEvictionInvalidatesOtherCacheInstances() {
        InMemoryPrincipalCacheInvalidationBus bus = new InMemoryPrincipalCacheInvalidationBus();
        SecurityPrincipalCache nodeA = new SecurityPrincipalCache(clock, Duration.ofMinutes(5), bus);
        SecurityPrincipalCache nodeB = new SecurityPrincipalCache(clock, Duration.ofMinutes(5), bus);
        AtomicInteger nodeBLoads = new AtomicInteger();

        ErpPrincipal first = nodeB.get(7L, () -> principal(7L, "permission:" + nodeBLoads.incrementAndGet()));
        nodeA.evictUser(7L);
        ErpPrincipal reloaded = nodeB.get(7L, () -> principal(7L, "permission:" + nodeBLoads.incrementAndGet()));

        assertThat(reloaded).isNotSameAs(first);
        assertThat(reloaded.permissions()).containsExactly("permission:2");
        assertThat(nodeBLoads).hasValue(2);
    }

    private static ErpPrincipal principal(Long userId, String permission) {
        return new ErpPrincipal(
                userId,
                1L,
                1L,
                "user" + userId,
                "User " + userId,
                "{noop}password",
                Set.of(permission)
        );
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    private static final class InMemoryPrincipalCacheInvalidationBus implements PrincipalCacheInvalidationBus {

        private final List<Consumer<PrincipalCacheInvalidationEvent>> listeners = new CopyOnWriteArrayList<>();

        @Override
        public void publish(PrincipalCacheInvalidationEvent event) {
            listeners.forEach(listener -> listener.accept(event));
        }

        @Override
        public void subscribe(Consumer<PrincipalCacheInvalidationEvent> listener) {
            listeners.add(listener);
        }
    }
}
