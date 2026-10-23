package com.tuowei.erp.masterdata.warehouse;

import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.masterdata.location.service.LocationService;
import com.tuowei.erp.masterdata.warehouse.mapper.WarehouseMapper;
import com.tuowei.erp.masterdata.warehouse.service.WarehouseCommandService;
import com.tuowei.erp.masterdata.warehouse.service.WarehouseQueryService;
import com.tuowei.erp.masterdata.warehouse.service.WarehouseService;
import com.tuowei.erp.masterdata.warehouse.web.WarehouseCreateRequest;
import com.tuowei.erp.masterdata.warehouse.web.WarehousePageQuery;
import com.tuowei.erp.masterdata.warehouse.web.WarehouseUpdateRequest;
import com.tuowei.erp.system.dept.mapper.DeptMapper;
import com.tuowei.erp.system.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WarehouseServiceDecompositionTest {

    @Test
    void facadeAndCollaboratorsHaveOneWayDependencies() {
        assertThat(constructorDependencies(WarehouseService.class))
                .containsExactlyInAnyOrder(WarehouseQueryService.class, WarehouseCommandService.class);
        assertThat(constructorDependencies(WarehouseQueryService.class))
                .containsExactlyInAnyOrder(WarehouseMapper.class, AuditMetadataFactory.class)
                .doesNotContain(WarehouseService.class, WarehouseCommandService.class);
        assertThat(constructorDependencies(WarehouseCommandService.class))
                .containsExactlyInAnyOrder(
                        WarehouseMapper.class,
                        DeptMapper.class,
                        UserMapper.class,
                        AuditMetadataFactory.class,
                        LocationService.class,
                        WarehouseQueryService.class
                )
                .doesNotContain(WarehouseService.class);
    }

    @Test
    void facadeDelegatesAllApisAndNormalizesNullListQuery() {
        WarehouseQueryService queryService = mock(WarehouseQueryService.class);
        WarehouseCommandService commandService = mock(WarehouseCommandService.class);
        WarehouseService service = new WarehouseService(queryService, commandService);
        WarehouseCreateRequest createRequest = new WarehouseCreateRequest("W-1", "仓库", 1L, 2L, null, null);
        WarehouseUpdateRequest updateRequest = new WarehouseUpdateRequest("仓库更新", 1L, 2L, null, null);
        WarehousePageQuery query = new WarehousePageQuery();
        StreamingResponseBody export = outputStream -> { };
        when(queryService.exportWarehouses(query)).thenReturn(export);

        service.create(createRequest);
        service.getById(10L);
        service.list(null);
        assertThat(service.exportWarehouses(query)).isSameAs(export);
        service.update(10L, updateRequest);
        service.enable(10L);
        service.disable(10L);

        verify(commandService).create(createRequest);
        verify(queryService).getById(10L);
        verify(queryService).list(any(WarehousePageQuery.class));
        verify(queryService).exportWarehouses(query);
        verify(commandService).update(10L, updateRequest);
        verify(commandService).enable(10L);
        verify(commandService).disable(10L);
    }

    @Test
    void facadeAndQueryKeepReadOnlyTransactionsAndExportHasNone() throws NoSuchMethodException {
        for (Class<?> type : new Class<?>[]{WarehouseService.class, WarehouseQueryService.class}) {
            assertReadOnly(type.getDeclaredMethod("list", WarehousePageQuery.class));
            assertReadOnly(type.getDeclaredMethod("getById", Long.class));
            assertThat(type.getDeclaredMethod("exportWarehouses", WarehousePageQuery.class)
                    .getAnnotation(Transactional.class)).isNull();
        }
    }

    @Test
    void facadeAndCommandKeepRequiredWriteTransactions() throws NoSuchMethodException {
        for (Class<?> type : new Class<?>[]{WarehouseService.class, WarehouseCommandService.class}) {
            assertRequired(type.getDeclaredMethod("create", WarehouseCreateRequest.class));
            assertRequired(type.getDeclaredMethod("update", Long.class, WarehouseUpdateRequest.class));
            assertRequired(type.getDeclaredMethod("enable", Long.class));
            assertRequired(type.getDeclaredMethod("disable", Long.class));
        }
    }

    private Set<Class<?>> constructorDependencies(Class<?> type) {
        return Arrays.stream(type.getDeclaredConstructors())
                .flatMap(constructor -> Arrays.stream(constructor.getParameterTypes()))
                .collect(Collectors.toSet());
    }

    private void assertReadOnly(java.lang.reflect.Method method) {
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isTrue();
    }

    private void assertRequired(java.lang.reflect.Method method) {
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isFalse();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRED);
    }
}
