package com.tuowei.erp.common.config;

import com.tuowei.erp.common.cache.CacheService;
import com.tuowei.erp.common.cache.RedisCacheService;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class CacheServiceStructureTest {

    @Test
    void cacheServiceDefinesCacheAsideEntryPoint() throws NoSuchMethodException {
        assertThat(CacheService.class.isInterface()).isTrue();
        assertThat(CacheService.class.getMethod("getOrLoad", String.class, Duration.class, Supplier.class)
                .getReturnType()).isEqualTo(String.class);
        assertThat(CacheService.class.getMethod("evict", String.class)
                .getReturnType()).isEqualTo(Void.TYPE);
        assertThat(CacheService.class.getMethod("evictByPrefix", String.class)
                .getReturnType()).isEqualTo(Void.TYPE);
    }

    @Test
    void redisCacheServiceIsSpringServiceImplementation() {
        assertThat(RedisCacheService.class).isAssignableTo(CacheService.class);
        assertThat(RedisCacheService.class.isAnnotationPresent(Service.class)).isTrue();
    }
}
