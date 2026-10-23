package com.tuowei.erp.masterdata.location.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.exception.BusinessConflictException;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.masterdata.location.mapper.LocationMapper;
import com.tuowei.erp.masterdata.location.model.LocationEntity;
import com.tuowei.erp.masterdata.location.web.LocationCreateRequest;
import com.tuowei.erp.masterdata.location.web.LocationResponse;
import com.tuowei.erp.masterdata.location.web.LocationUpdateRequest;
import com.tuowei.erp.masterdata.warehouse.mapper.WarehouseMapper;
import com.tuowei.erp.masterdata.warehouse.model.WarehouseEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocationCommandServiceTest {

    private static final AuditMetadata AUDIT = new AuditMetadata(
            7201L, 8201L, 9201L, LocalDateTime.of(2026, 8, 21, 9, 30)
    );
    private static final Long LOCATION_ID = 301L;
    private static final Long WAREHOUSE_ID = 302L;

    @Mock
    private LocationMapper locationMapper;
    @Mock
    private WarehouseMapper warehouseMapper;
    @Mock
    private AuditMetadataFactory auditMetadataFactory;
    @Mock
    private LocationQueryService locationQueryService;

    @BeforeEach
    void setUp() {
        lenient().when(auditMetadataFactory.current()).thenReturn(AUDIT);
    }

    @Test
    void createNormalizesFieldsMakesFirstLocationDefaultAndScopesTenant() {
        WarehouseEntity warehouse = warehouse(AUDIT.companyId(), AUDIT.accountBookId());
        when(warehouseMapper.selectById(WAREHOUSE_ID)).thenReturn(warehouse);
        when(locationMapper.selectCount(any())).thenReturn(0L, 0L);
        when(locationMapper.insert(any(LocationEntity.class))).thenAnswer(invocation -> {
            LocationEntity entity = invocation.getArgument(0);
            entity.setId(LOCATION_ID);
            return 1;
        });
        LocationResponse expected = response(true, "ACTIVE");
        when(locationQueryService.toResponse(any(LocationEntity.class), eq("成品仓"))).thenReturn(expected);

        LocationResponse actual = service().create(new LocationCreateRequest(
                WAREHOUSE_ID, "  a-01 ", "  A区01  ", false, "  备注  "
        ));

        assertThat(actual).isSameAs(expected);
        ArgumentCaptor<LocationEntity> entityCaptor = ArgumentCaptor.forClass(LocationEntity.class);
        verify(locationMapper).insert(entityCaptor.capture());
        LocationEntity inserted = entityCaptor.getValue();
        assertThat(inserted.getCompanyId()).isEqualTo(AUDIT.companyId());
        assertThat(inserted.getAccountBookId()).isEqualTo(AUDIT.accountBookId());
        assertThat(inserted.getWarehouseId()).isEqualTo(WAREHOUSE_ID);
        assertThat(inserted.getLocationCode()).isEqualTo("A-01");
        assertThat(inserted.getLocationName()).isEqualTo("A区01");
        assertThat(inserted.getIsDefault()).isEqualTo(1);
        assertThat(inserted.getStatus()).isEqualTo("ACTIVE");
        assertThat(inserted.getRemark()).isEqualTo("备注");
        assertThat(inserted.getCreatedBy()).isEqualTo(AUDIT.userId());
        assertThat(inserted.getCreatedTime()).isEqualTo(AUDIT.now());
        assertThat(inserted.getVersion()).isZero();
    }

    @Test
    void createRejectsWarehouseFromAnotherTenantBeforeWriting() {
        when(warehouseMapper.selectById(WAREHOUSE_ID)).thenReturn(warehouse(AUDIT.companyId(), 9999L));

        assertThatThrownBy(() -> service().create(createRequest("A-01", "A区01", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("仓库不存在");

        verify(locationMapper, never()).insert(any(LocationEntity.class));
    }

    @Test
    void createRejectsDuplicateCodeWithinWarehouse() {
        when(warehouseMapper.selectById(WAREHOUSE_ID)).thenReturn(warehouse(AUDIT.companyId(), AUDIT.accountBookId()));
        when(locationMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service().create(createRequest("a-01", "A区01", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("同一仓库下库位编码已存在");

        verify(locationMapper, never()).insert(any(LocationEntity.class));
    }

    @Test
    void ensureDefaultLocationIsIdempotentAndUsesProvidedAuditForCreation() {
        AuditMetadata explicitAudit = new AuditMetadata(
                7202L, 8202L, 9202L, LocalDateTime.of(2026, 8, 21, 10, 0)
        );
        WarehouseEntity warehouse = warehouse(explicitAudit.companyId(), explicitAudit.accountBookId());
        when(locationMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(warehouseMapper.selectById(WAREHOUSE_ID)).thenReturn(warehouse);
        when(locationMapper.selectCount(any())).thenReturn(0L, 0L);
        when(locationMapper.insert(any(LocationEntity.class))).thenAnswer(invocation -> {
            LocationEntity entity = invocation.getArgument(0);
            entity.setId(LOCATION_ID);
            return 1;
        });
        LocationResponse expected = response(true, "ACTIVE");
        when(locationQueryService.toResponse(any(LocationEntity.class), eq("成品仓"))).thenReturn(expected);

        LocationResponse actual = service().ensureDefaultLocation(warehouse, explicitAudit);

        assertThat(actual).isSameAs(expected);
        ArgumentCaptor<LocationEntity> entityCaptor = ArgumentCaptor.forClass(LocationEntity.class);
        verify(locationMapper).insert(entityCaptor.capture());
        assertThat(entityCaptor.getValue().getCompanyId()).isEqualTo(explicitAudit.companyId());
        assertThat(entityCaptor.getValue().getAccountBookId()).isEqualTo(explicitAudit.accountBookId());
        assertThat(entityCaptor.getValue().getLocationCode()).isEqualTo("MAIN");
        assertThat(entityCaptor.getValue().getLocationName()).isEqualTo("默认库位");
        verify(auditMetadataFactory, never()).current();

        LocationEntity existing = location(1);
        when(locationMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);
        when(locationQueryService.toResponse(existing, "成品仓")).thenReturn(expected);
        assertThat(service().ensureDefaultLocation(warehouse, explicitAudit)).isSameAs(expected);
        verify(locationMapper, org.mockito.Mockito.times(1)).insert(any(LocationEntity.class));
    }

    @Test
    void updateNormalizesFieldsKeepsDefaultWhenFlagIsNullAndUsesOptimisticLock() {
        LocationEntity existing = location(1);
        when(locationQueryService.requireLocation(LOCATION_ID, AUDIT)).thenReturn(existing);
        when(locationMapper.selectCount(any())).thenReturn(0L);
        when(locationMapper.updateById(existing)).thenReturn(1);
        LocationResponse expected = response(true, "INACTIVE");
        when(locationQueryService.toResponse(existing)).thenReturn(expected);

        LocationResponse actual = service().update(LOCATION_ID, new LocationUpdateRequest(
                "  b-02 ", "  B区02  ", null, " inactive ", "  updated  "
        ));

        assertThat(actual).isSameAs(expected);
        assertThat(existing.getLocationCode()).isEqualTo("B-02");
        assertThat(existing.getLocationName()).isEqualTo("B区02");
        assertThat(existing.getIsDefault()).isEqualTo(1);
        assertThat(existing.getStatus()).isEqualTo("INACTIVE");
        assertThat(existing.getRemark()).isEqualTo("updated");
        assertThat(existing.getUpdatedBy()).isEqualTo(AUDIT.userId());
        verify(locationMapper).updateById(existing);
    }

    @Test
    void updateCanSwitchDefaultButCannotUnsetCurrentDefault() {
        LocationEntity existing = location(0);
        when(locationQueryService.requireLocation(LOCATION_ID, AUDIT)).thenReturn(existing);
        when(locationMapper.selectCount(any())).thenReturn(0L);
        when(locationMapper.update(isNull(), any())).thenReturn(1);
        when(locationMapper.updateById(existing)).thenReturn(1);
        when(locationQueryService.toResponse(existing)).thenReturn(response(true, "ACTIVE"));

        service().update(LOCATION_ID, new LocationUpdateRequest(
                "B-02", "B区02", true, null, null
        ));
        assertThat(existing.getIsDefault()).isEqualTo(1);
        verify(locationMapper).update(isNull(), any());

        LocationEntity defaultLocation = location(1);
        when(locationQueryService.requireLocation(LOCATION_ID, AUDIT)).thenReturn(defaultLocation);
        assertThatThrownBy(() -> service().update(LOCATION_ID, new LocationUpdateRequest(
                "B-02", "B区02", false, null, null
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("至少保留一个默认库位");
    }

    @Test
    void disableProtectsDefaultAndUpdatesNonDefaultWithOptimisticLock() {
        LocationEntity defaultLocation = location(1);
        when(locationQueryService.requireLocation(LOCATION_ID, AUDIT)).thenReturn(defaultLocation);
        assertThatThrownBy(() -> service().disable(LOCATION_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("默认库位不能停用");

        LocationEntity nonDefault = location(0);
        when(locationQueryService.requireLocation(LOCATION_ID, AUDIT)).thenReturn(nonDefault);
        when(locationMapper.updateById(nonDefault)).thenReturn(1);
        when(locationQueryService.toResponse(nonDefault)).thenReturn(response(false, "INACTIVE"));
        LocationResponse response = service().disable(LOCATION_ID);
        assertThat(response.status()).isEqualTo("INACTIVE");
        assertThat(nonDefault.getStatus()).isEqualTo("INACTIVE");
    }

    @Test
    void updateSurfacesOptimisticLockConflict() {
        LocationEntity existing = location(0);
        when(locationQueryService.requireLocation(LOCATION_ID, AUDIT)).thenReturn(existing);
        when(locationMapper.selectCount(any())).thenReturn(0L);
        when(locationMapper.updateById(existing)).thenReturn(0);

        assertThatThrownBy(() -> service().update(LOCATION_ID, new LocationUpdateRequest(
                "B-02", "B区02", null, null, null
        )))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessage("库位已被其他操作修改，请刷新后重试");

        verify(locationQueryService, never()).toResponse(existing);
    }

    @Test
    void updateRejectsUnsupportedStatus() {
        LocationEntity existing = location(0);
        when(locationQueryService.requireLocation(LOCATION_ID, AUDIT)).thenReturn(existing);
        when(locationMapper.selectCount(any())).thenReturn(0L);

        assertThatThrownBy(() -> service().update(LOCATION_ID, new LocationUpdateRequest(
                "B-02", "B区02", null, "paused", null
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("状态仅支持 ACTIVE/INACTIVE");

        verify(locationMapper, never()).updateById(any(LocationEntity.class));
    }

    private LocationCommandService service() {
        return new LocationCommandService(locationMapper, warehouseMapper, auditMetadataFactory, locationQueryService);
    }

    private LocationCreateRequest createRequest(String code, String name, Boolean isDefault) {
        return new LocationCreateRequest(WAREHOUSE_ID, code, name, isDefault, null);
    }

    private WarehouseEntity warehouse(Long companyId, Long accountBookId) {
        WarehouseEntity entity = new WarehouseEntity();
        entity.setId(WAREHOUSE_ID);
        entity.setCompanyId(companyId);
        entity.setAccountBookId(accountBookId);
        entity.setWarehouseName("成品仓");
        entity.setDeletedFlag(0);
        return entity;
    }

    private LocationEntity location(int isDefault) {
        LocationEntity entity = new LocationEntity();
        entity.setId(LOCATION_ID);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(AUDIT.accountBookId());
        entity.setWarehouseId(WAREHOUSE_ID);
        entity.setLocationCode("A-01");
        entity.setLocationName("A区01");
        entity.setIsDefault(isDefault);
        entity.setStatus("ACTIVE");
        entity.setDeletedFlag(0);
        entity.setRemark("command test");
        entity.setVersion(0);
        return entity;
    }

    private LocationResponse response(boolean isDefault, String status) {
        return new LocationResponse(
                LOCATION_ID, WAREHOUSE_ID, "成品仓", "A-01", "A区01", isDefault, status, "command test"
        );
    }
}
