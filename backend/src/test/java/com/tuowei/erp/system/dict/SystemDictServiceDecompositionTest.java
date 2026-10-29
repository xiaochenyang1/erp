package com.tuowei.erp.system.dict;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuowei.erp.common.cache.CacheService;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.system.dict.mapper.DictItemMapper;
import com.tuowei.erp.system.dict.mapper.DictTypeMapper;
import com.tuowei.erp.system.dict.service.SystemDictCommandService;
import com.tuowei.erp.system.dict.service.SystemDictQueryService;
import com.tuowei.erp.system.dict.service.SystemDictService;
import com.tuowei.erp.system.dict.web.DictItemCreateRequest;
import com.tuowei.erp.system.dict.web.DictItemUpdateRequest;
import com.tuowei.erp.system.dict.web.DictTypeCreateRequest;
import com.tuowei.erp.system.dict.web.DictTypePageQuery;
import com.tuowei.erp.system.dict.web.DictTypeUpdateRequest;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SystemDictServiceDecompositionTest {

    @Test
    void facadeAndCollaboratorsHaveOneWayDependencies() {
        assertThat(constructorDependencies(SystemDictService.class))
                .containsExactlyInAnyOrder(SystemDictQueryService.class, SystemDictCommandService.class);
        assertThat(constructorDependencies(SystemDictQueryService.class))
                .containsExactlyInAnyOrder(DictTypeMapper.class, DictItemMapper.class, CacheService.class, ObjectMapper.class)
                .doesNotContain(SystemDictService.class, SystemDictCommandService.class);
        assertThat(constructorDependencies(SystemDictCommandService.class))
                .containsExactlyInAnyOrder(
                        DictTypeMapper.class,
                        DictItemMapper.class,
                        AuditMetadataFactory.class,
                        SystemDictQueryService.class
                )
                .doesNotContain(SystemDictService.class);
    }

    @Test
    void facadeDelegatesAllTwelveApisAndNormalizesNullTypeQuery() {
        SystemDictQueryService queryService = mock(SystemDictQueryService.class);
        SystemDictCommandService commandService = mock(SystemDictCommandService.class);
        SystemDictService service = new SystemDictService(queryService, commandService);
        DictTypeCreateRequest typeCreate = new DictTypeCreateRequest("status", "状态", null);
        DictTypeUpdateRequest typeUpdate = new DictTypeUpdateRequest("新状态", null);
        DictItemCreateRequest itemCreate = new DictItemCreateRequest("status", "已启用", "ACTIVE", 1, null);
        DictItemUpdateRequest itemUpdate = new DictItemUpdateRequest("启用", 2, null);

        service.createType(typeCreate);
        service.listTypes(null);
        service.getTypeById(1L);
        service.updateType(1L, typeUpdate);
        service.enableType(1L);
        service.disableType(1L);
        service.createItem(itemCreate);
        service.listItems("status");
        service.requireEnabledItem("status", "ACTIVE", "不可用");
        service.updateItem(2L, itemUpdate);
        service.enableItem(2L);
        service.disableItem(2L);

        verify(commandService).createType(typeCreate);
        verify(queryService).listTypes(any(DictTypePageQuery.class));
        verify(queryService).getTypeById(1L);
        verify(commandService).updateType(1L, typeUpdate);
        verify(commandService).enableType(1L);
        verify(commandService).disableType(1L);
        verify(commandService).createItem(itemCreate);
        verify(queryService).listItems("status");
        verify(queryService).requireEnabledItem("status", "ACTIVE", "不可用");
        verify(commandService).updateItem(2L, itemUpdate);
        verify(commandService).enableItem(2L);
        verify(commandService).disableItem(2L);
    }

    @Test
    void facadeAndQueryKeepReadOnlyTransactions() throws NoSuchMethodException {
        for (Class<?> type : new Class<?>[]{SystemDictService.class, SystemDictQueryService.class}) {
            assertReadOnly(type.getDeclaredMethod("listTypes", DictTypePageQuery.class));
            assertReadOnly(type.getDeclaredMethod("getTypeById", Long.class));
            assertReadOnly(type.getDeclaredMethod("listItems", String.class));
            assertReadOnly(type.getDeclaredMethod("requireEnabledItem", String.class, String.class, String.class));
        }
    }

    @Test
    void facadeAndCommandKeepRequiredWriteTransactions() throws NoSuchMethodException {
        Class<?>[] types = {SystemDictService.class, SystemDictCommandService.class};
        for (Class<?> type : types) {
            assertRequiredWrite(type.getDeclaredMethod("createType", DictTypeCreateRequest.class));
            assertRequiredWrite(type.getDeclaredMethod("updateType", Long.class, DictTypeUpdateRequest.class));
            assertRequiredWrite(type.getDeclaredMethod("enableType", Long.class));
            assertRequiredWrite(type.getDeclaredMethod("disableType", Long.class));
            assertRequiredWrite(type.getDeclaredMethod("createItem", DictItemCreateRequest.class));
            assertRequiredWrite(type.getDeclaredMethod("updateItem", Long.class, DictItemUpdateRequest.class));
            assertRequiredWrite(type.getDeclaredMethod("enableItem", Long.class));
            assertRequiredWrite(type.getDeclaredMethod("disableItem", Long.class));
        }
    }

    private Set<Class<?>> constructorDependencies(Class<?> type) {
        return Arrays.stream(type.getDeclaredConstructors())
                .flatMap(constructor -> Arrays.stream(constructor.getParameterTypes()))
                .collect(Collectors.toSet());
    }

    private void assertReadOnly(Method method) {
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isTrue();
    }

    private void assertRequiredWrite(Method method) {
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isFalse();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRED);
    }
}
