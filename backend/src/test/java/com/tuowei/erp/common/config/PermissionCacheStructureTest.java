package com.tuowei.erp.common.config;

import com.tuowei.erp.common.cache.CacheService;
import com.tuowei.erp.common.security.UserPermissionService;
import com.tuowei.erp.system.user.service.UserCommandService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionCacheStructureTest {

    @Test
    void userPermissionServiceDependsOnSharedCacheService() {
        assertThat(constructorParameterTypes(UserPermissionService.class))
                .anySatisfy(parameters -> assertThat(parameters).contains(CacheService.class));
    }

    @Test
    void userCommandServiceCanEvictPermissionCacheWhenRolesChange() {
        assertThat(constructorParameterTypes(UserCommandService.class))
                .anySatisfy(parameters -> assertThat(parameters).contains(UserPermissionService.class));
    }

    private static java.util.List<Class<?>[]> constructorParameterTypes(Class<?> type) {
        return Arrays.stream(type.getDeclaredConstructors())
                .map(Constructor::getParameterTypes)
                .toList();
    }
}
