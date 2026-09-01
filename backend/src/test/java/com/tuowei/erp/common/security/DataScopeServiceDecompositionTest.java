package com.tuowei.erp.common.security;

import com.tuowei.erp.system.datascope.mapper.RoleDataScopeMapper;
import com.tuowei.erp.system.datascope.mapper.UserDataScopeMapper;
import com.tuowei.erp.system.role.mapper.RoleMapper;
import com.tuowei.erp.system.user.mapper.UserRoleMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DataScopeServiceDecompositionTest {

    @Test
    void policyFacadeDependsOnSnapshotServiceWhileSnapshotServiceOwnsPersistence() {
        assertThat(autowiredConstructorDependencies(DataScopeService.class))
                .containsExactly(DataScopeSnapshotService.class);
        assertThat(constructorDependencies(DataScopeSnapshotService.class))
                .containsExactlyInAnyOrder(
                        UserRoleMapper.class,
                        RoleMapper.class,
                        RoleDataScopeMapper.class,
                        UserDataScopeMapper.class
                )
                .doesNotContain(DataScopeService.class);
    }

    @Test
    void facadeDelegatesSnapshotConstruction() {
        DataScopeSnapshotService snapshotService = mock(DataScopeSnapshotService.class);
        DataScopeSnapshot expected = DataScopeSnapshot.all();
        when(snapshotService.buildSnapshot(7L, 11L, 13L)).thenReturn(expected);

        DataScopeSnapshot actual = new DataScopeService(snapshotService).buildSnapshot(7L, 11L, 13L);

        assertThat(actual).isSameAs(expected);
        verify(snapshotService).buildSnapshot(7L, 11L, 13L);
    }

    @Test
    void legacyMapperConstructorRemainsAvailable() throws NoSuchMethodException {
        assertThat(DataScopeService.class.getDeclaredConstructor(
                UserRoleMapper.class,
                RoleMapper.class,
                RoleDataScopeMapper.class,
                UserDataScopeMapper.class
        )).isNotNull();
    }

    private Set<Class<?>> autowiredConstructorDependencies(Class<?> type) {
        return Arrays.stream(type.getDeclaredConstructors())
                .filter(constructor -> constructor.isAnnotationPresent(Autowired.class))
                .flatMap(constructor -> Arrays.stream(constructor.getParameterTypes()))
                .collect(Collectors.toSet());
    }

    private Set<Class<?>> constructorDependencies(Class<?> type) {
        return Arrays.stream(type.getDeclaredConstructors())
                .flatMap((Constructor<?> constructor) -> Arrays.stream(constructor.getParameterTypes()))
                .collect(Collectors.toSet());
    }
}
