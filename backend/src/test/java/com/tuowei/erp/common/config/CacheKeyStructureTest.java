package com.tuowei.erp.common.config;

import com.tuowei.erp.common.cache.CacheKeyBuilder;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.assertj.core.api.Assertions.assertThat;

class CacheKeyStructureTest {

    @Test
    void cacheKeyBuilderProvidesAccountBookScopedEntryPoint() throws NoSuchMethodException {
        Method method = CacheKeyBuilder.class.getDeclaredMethod(
                "accountBookScoped",
                Long.class,
                Long.class,
                String[].class
        );

        assertThat(Modifier.isStatic(method.getModifiers())).isTrue();
        assertThat(method.getReturnType()).isEqualTo(String.class);
    }

    @Test
    void cacheKeyBuilderProvidesGlobalEntryPoint() throws NoSuchMethodException {
        Method method = CacheKeyBuilder.class.getDeclaredMethod(
                "global",
                String[].class
        );

        assertThat(Modifier.isStatic(method.getModifiers())).isTrue();
        assertThat(method.getReturnType()).isEqualTo(String.class);
    }
}
