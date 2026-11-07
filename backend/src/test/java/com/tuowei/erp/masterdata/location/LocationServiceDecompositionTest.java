package com.tuowei.erp.masterdata.location;

import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.masterdata.location.mapper.LocationMapper;
import com.tuowei.erp.masterdata.location.service.LocationCommandService;
import com.tuowei.erp.masterdata.location.service.LocationQueryService;
import com.tuowei.erp.masterdata.location.service.LocationService;
import com.tuowei.erp.masterdata.location.web.LocationCreateRequest;
import com.tuowei.erp.masterdata.location.web.LocationPageQuery;
import com.tuowei.erp.masterdata.location.web.LocationResponse;
import com.tuowei.erp.masterdata.location.web.LocationUpdateRequest;
import com.tuowei.erp.masterdata.warehouse.mapper.WarehouseMapper;
import com.tuowei.erp.masterdata.warehouse.model.WarehouseEntity;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LocationServiceDecompositionTest {

    @Test
    void facadeAndCollaboratorsHaveOneWayDependencies() {
        assertThat(constructorDependencies(LocationService.class))
                .containsExactlyInAnyOrder(LocationQueryService.class, LocationCommandService.class);
        assertThat(constructorDependencies(LocationQueryService.class))
                .containsExactlyInAnyOrder(LocationMapper.class, WarehouseMapper.class, AuditMetadataFactory.class)
                .doesNotContain(LocationService.class, LocationCommandService.class);
        assertThat(constructorDependencies(LocationCommandService.class))
                .containsExactlyInAnyOrder(
                        LocationMapper.class,
                        WarehouseMapper.class,
                        AuditMetadataFactory.class,
                        LocationQueryService.class
                )
                .doesNotContain(LocationService.class);
    }

    @Test
    void facadeDelegatesAllSevenApisAndNormalizesNullListQuery() {
        LocationQueryService queryService = mock(LocationQueryService.class);
        LocationCommandService commandService = mock(LocationCommandService.class);
        LocationService service = new LocationService(queryService, commandService);
        LocationCreateRequest createRequest = new LocationCreateRequest(1L, "A-01", "库位", true, null);
        LocationUpdateRequest updateRequest = new LocationUpdateRequest("A-02", "新库位", null, "ACTIVE", null);
        LocationPageQuery query = new LocationPageQuery();
        WarehouseEntity warehouse = new WarehouseEntity();
        warehouse.setId(10L);
        AuditMetadata audit = new AuditMetadata(1L, 2L, 3L, LocalDateTime.of(2026, 8, 21, 10, 0));

        service.create(createRequest);
        service.ensureDefaultLocation(warehouse, audit);
        service.getById(10L);
        service.list(null);
        service.update(10L, updateRequest);
        service.enable(10L);
        service.disable(10L);

        verify(commandService).create(createRequest);
        verify(commandService).ensureDefaultLocation(warehouse, audit);
        verify(queryService).getById(10L);
        verify(queryService).list(any(LocationPageQuery.class));
        verify(commandService).update(10L, updateRequest);
        verify(commandService).enable(10L);
        verify(commandService).disable(10L);
    }

    @Test
    void facadeAndQueryKeepReadOnlyTransactions() throws NoSuchMethodException {
        for (Class<?> type : new Class<?>[]{LocationService.class, LocationQueryService.class}) {
            assertReadOnly(type.getDeclaredMethod("list", LocationPageQuery.class));
            assertReadOnly(type.getDeclaredMethod("getById", Long.class));
        }
    }

    @Test
    void facadeAndCommandKeepRequiredWriteTransactions() throws NoSuchMethodException {
        for (Class<?> type : new Class<?>[]{LocationService.class, LocationCommandService.class}) {
            assertRequiredWrite(type.getDeclaredMethod("create", LocationCreateRequest.class));
            assertRequiredWrite(type.getDeclaredMethod("update", Long.class, LocationUpdateRequest.class));
            assertRequiredWrite(type.getDeclaredMethod("enable", Long.class));
            assertRequiredWrite(type.getDeclaredMethod("disable", Long.class));
        }
        assertRequiredWrite(LocationService.class.getDeclaredMethod(
                "ensureDefaultLocation", WarehouseEntity.class, AuditMetadata.class
        ));
        assertRequiredWrite(LocationCommandService.class.getDeclaredMethod(
                "ensureDefaultLocation", WarehouseEntity.class, AuditMetadata.class
        ));
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
