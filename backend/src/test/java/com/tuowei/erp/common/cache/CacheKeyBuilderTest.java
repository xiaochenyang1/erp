package com.tuowei.erp.common.cache;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CacheKeyBuilderTest {

    @Test
    void buildsAccountBookScopedKey() {
        String key = CacheKeyBuilder.accountBookScoped(10L, 20L, "dict", "items", "order_status");

        assertThat(key).isEqualTo("erp:10:20:dict:items:order_status");
    }

    @Test
    void buildsGlobalKey() {
        String key = CacheKeyBuilder.global("dict", "items", "order_status");

        assertThat(key).isEqualTo("erp:global:dict:items:order_status");
    }

    @Test
    void trimsSegmentsAndNormalizesBlankPadding() {
        String key = CacheKeyBuilder.accountBookScoped(10L, 20L, " permission ", " user ", " 7 ");

        assertThat(key).isEqualTo("erp:10:20:permission:user:7");
    }

    @Test
    void rejectsMissingTenantScope() {
        assertThatThrownBy(() -> CacheKeyBuilder.accountBookScoped(null, 20L, "dict", "items"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("cache key companyId must not be null");

        assertThatThrownBy(() -> CacheKeyBuilder.accountBookScoped(10L, null, "dict", "items"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("cache key accountBookId must not be null");
    }

    @Test
    void rejectsNonPositiveTenantScope() {
        assertThatThrownBy(() -> CacheKeyBuilder.accountBookScoped(0L, 20L, "dict", "items"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("cache key companyId must be positive");

        assertThatThrownBy(() -> CacheKeyBuilder.accountBookScoped(10L, -1L, "dict", "items"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("cache key accountBookId must be positive");
    }

    @Test
    void rejectsUnsafeSegments() {
        assertThatThrownBy(() -> CacheKeyBuilder.accountBookScoped(10L, 20L, "dict", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("cache key segment must not be blank");

        assertThatThrownBy(() -> CacheKeyBuilder.accountBookScoped(10L, 20L, "dict:items"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("cache key segment must not contain ':'");

        assertThatThrownBy(() -> CacheKeyBuilder.global("dict", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("cache key segment must not be blank");
    }
}
