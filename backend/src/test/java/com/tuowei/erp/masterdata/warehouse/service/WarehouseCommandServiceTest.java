package com.tuowei.erp.masterdata.warehouse.service;

import com.tuowei.erp.common.exception.BusinessConflictException;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.masterdata.location.service.LocationService;
import com.tuowei.erp.masterdata.warehouse.mapper.WarehouseMapper;
import com.tuowei.erp.masterdata.warehouse.model.WarehouseEntity;
import com.tuowei.erp.masterdata.warehouse.web.WarehouseCreateRequest;
import com.tuowei.erp.masterdata.warehouse.web.WarehouseResponse;
import com.tuowei.erp.masterdata.warehouse.web.WarehouseUpdateRequest;
import com.tuowei.erp.system.dept.mapper.DeptMapper;
import com.tuowei.erp.system.dept.model.DeptEntity;
import com.tuowei.erp.system.user.mapper.UserMapper;
import com.tuowei.erp.system.user.model.UserEntity;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WarehouseCommandServiceTest {

    private static final AuditMetadata AUDIT = new AuditMetadata(
            7601L, 8601L, 9601L, LocalDateTime.of(2026, 8, 20, 18, 30)
    );
    private static final Long WAREHOUSE_ID = 601L;
    private static final Long DEPT_ID = 602L;
    private static final Long MANAGER_ID = 603L;

    @Mock
    private WarehouseMapper warehouseMapper;
    @Mock
    private DeptMapper deptMapper;
    @Mock
    private UserMapper userMapper;
    @Mock
    private AuditMetadataFactory auditMetadataFactory;
    @Mock
    private LocationService locationService;
    @Mock
    private WarehouseQueryService warehouseQueryService;

    @BeforeEach
    void setUp() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
    }

    @Test
    void createBuildsTenantWarehouseAndEnsuresDefaultLocation() {
        when(deptMapper.selectById(DEPT_ID)).thenReturn(dept(AUDIT.accountBookId()));
        when(userMapper.selectById(MANAGER_ID)).thenReturn(user(AUDIT.accountBookId()));
        when(warehouseMapper.insert(any(WarehouseEntity.class))).thenAnswer(invocation -> {
            WarehouseEntity entity = invocation.getArgument(0);
            entity.setId(WAREHOUSE_ID);
            return 1;
        });
        WarehouseResponse expected = response("ACTIVE");
        when(warehouseQueryService.toResponse(any(WarehouseEntity.class))).thenReturn(expected);

        WarehouseResponse actual = service().create(createRequest());

        assertThat(actual).isSameAs(expected);
        ArgumentCaptor<WarehouseEntity> entityCaptor = ArgumentCaptor.forClass(WarehouseEntity.class);
        verify(warehouseMapper).insert(entityCaptor.capture());
        WarehouseEntity inserted = entityCaptor.getValue();
        assertThat(inserted.getCompanyId()).isEqualTo(AUDIT.companyId());
        assertThat(inserted.getAccountBookId()).isEqualTo(AUDIT.accountBookId());
        assertThat(inserted.getWarehouseCode()).isEqualTo("W-001");
        assertThat(inserted.getStatus()).isEqualTo("ACTIVE");
        assertThat(inserted.getDeletedFlag()).isZero();
        assertThat(inserted.getCreatedBy()).isEqualTo(AUDIT.userId());
        assertThat(inserted.getVersion()).isZero();
        verify(locationService).ensureDefaultLocation(inserted, AUDIT);
    }

    @Test
    void createRejectsDepartmentFromAnotherAccountBookBeforeWriting() {
        when(deptMapper.selectById(DEPT_ID)).thenReturn(dept(9999L));

        assertThatThrownBy(() -> service().create(createRequest()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("部门不存在");

        verify(warehouseMapper, never()).insert(any(WarehouseEntity.class));
        verify(locationService, never()).ensureDefaultLocation(any(), any());
    }

    @Test
    void updatePreservesCodeAndSurfacesOptimisticConflict() {
        WarehouseEntity existing = warehouse(AUDIT.accountBookId());
        when(warehouseQueryService.requireWarehouse(WAREHOUSE_ID)).thenReturn(existing);
        when(deptMapper.selectById(DEPT_ID)).thenReturn(dept(AUDIT.accountBookId()));
        when(userMapper.selectById(MANAGER_ID)).thenReturn(user(AUDIT.accountBookId()));
        when(warehouseMapper.updateById(existing)).thenReturn(0);

        assertThatThrownBy(() -> service().update(WAREHOUSE_ID, updateRequest()))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessage("仓库已被其他操作修改，请刷新后重试");

        assertThat(existing.getWarehouseCode()).isEqualTo("W-001");
        assertThat(existing.getWarehouseName()).isEqualTo("成品仓更新");
        verify(warehouseQueryService, never()).toResponse(any(WarehouseEntity.class));
    }

    @Test
    void disableUpdatesStatusAndAuditFields() {
        WarehouseEntity existing = warehouse(AUDIT.accountBookId());
        when(warehouseQueryService.requireWarehouse(WAREHOUSE_ID)).thenReturn(existing);
        when(warehouseMapper.updateById(existing)).thenReturn(1);
        WarehouseResponse expected = response("INACTIVE");
        when(warehouseQueryService.toResponse(existing)).thenReturn(expected);

        WarehouseResponse actual = service().disable(WAREHOUSE_ID);

        assertThat(actual).isSameAs(expected);
        assertThat(existing.getStatus()).isEqualTo("INACTIVE");
        assertThat(existing.getUpdatedBy()).isEqualTo(AUDIT.userId());
        assertThat(existing.getUpdatedTime()).isEqualTo(AUDIT.now());
    }

    private WarehouseCommandService service() {
        return new WarehouseCommandService(
                warehouseMapper,
                deptMapper,
                userMapper,
                auditMetadataFactory,
                locationService,
                warehouseQueryService
        );
    }

    private WarehouseCreateRequest createRequest() {
        return new WarehouseCreateRequest("W-001", "成品仓", DEPT_ID, MANAGER_ID, "苏州", "command test");
    }

    private WarehouseUpdateRequest updateRequest() {
        return new WarehouseUpdateRequest("成品仓更新", DEPT_ID, MANAGER_ID, "杭州", "updated");
    }

    private WarehouseEntity warehouse(Long accountBookId) {
        WarehouseEntity entity = new WarehouseEntity();
        entity.setId(WAREHOUSE_ID);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(accountBookId);
        entity.setWarehouseCode("W-001");
        entity.setWarehouseName("成品仓");
        entity.setDeptId(DEPT_ID);
        entity.setManagerUserId(MANAGER_ID);
        entity.setStatus("ACTIVE");
        entity.setDeletedFlag(0);
        entity.setVersion(0);
        return entity;
    }

    private DeptEntity dept(Long accountBookId) {
        DeptEntity entity = new DeptEntity();
        entity.setId(DEPT_ID);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(accountBookId);
        entity.setDeletedFlag(0);
        return entity;
    }

    private UserEntity user(Long accountBookId) {
        UserEntity entity = new UserEntity();
        entity.setId(MANAGER_ID);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(accountBookId);
        entity.setDeletedFlag(0);
        return entity;
    }

    private WarehouseResponse response(String status) {
        return new WarehouseResponse(
                WAREHOUSE_ID, "W-001", "成品仓", DEPT_ID, MANAGER_ID, "苏州", status, "command test"
        );
    }
}
