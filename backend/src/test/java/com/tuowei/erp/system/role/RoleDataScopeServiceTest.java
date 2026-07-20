package com.tuowei.erp.system.role;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.security.SecurityPrincipalCache;
import com.tuowei.erp.masterdata.warehouse.mapper.WarehouseMapper;
import com.tuowei.erp.masterdata.warehouse.model.WarehouseEntity;
import com.tuowei.erp.system.datascope.mapper.RoleDataScopeMapper;
import com.tuowei.erp.system.datascope.model.RoleDataScopeEntity;
import com.tuowei.erp.system.role.mapper.RoleMapper;
import com.tuowei.erp.system.role.model.RoleEntity;
import com.tuowei.erp.system.role.service.RoleDataScopeService;
import com.tuowei.erp.system.role.web.RoleDataScopeAssignRequest;
import com.tuowei.erp.system.role.web.RoleDataScopeResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleDataScopeServiceTest {

    @Mock private RoleMapper roleMapper;
    @Mock private RoleDataScopeMapper roleDataScopeMapper;
    @Mock private WarehouseMapper warehouseMapper;
    @Mock private AuditMetadataFactory auditMetadataFactory;
    @Mock private SecurityPrincipalCache principalCache;

    private RoleDataScopeService service;

    @BeforeEach
    void setUp() {
        service = new RoleDataScopeService(
                roleMapper,
                roleDataScopeMapper,
                warehouseMapper,
                auditMetadataFactory,
                principalCache
        );
    }

    @Test
    void assignWarehouseScopePersistsRowsAndEvictsAllPrincipalCache() {
        when(auditMetadataFactory.current()).thenReturn(audit());
        when(roleMapper.selectById(3002L)).thenReturn(activeRole(3002L));
        when(warehouseMapper.selectById(4501L)).thenReturn(activeWarehouse(4501L));
        when(roleDataScopeMapper.delete(any(LambdaQueryWrapper.class))).thenReturn(1);
        when(roleDataScopeMapper.insert(any(RoleDataScopeEntity.class))).thenReturn(1);

        RoleDataScopeResponse response = service.assign(3002L, new RoleDataScopeAssignRequest(
                false, true, false, true, List.of(4501L, 4501L)
        ));

        ArgumentCaptor<RoleDataScopeEntity> captor = ArgumentCaptor.forClass(RoleDataScopeEntity.class);
        verify(roleDataScopeMapper, atLeastOnce()).insert(captor.capture());
        assertThat(captor.getAllValues()).extracting(RoleDataScopeEntity::getScopeType)
                .containsExactlyInAnyOrder("DEPT", "SELF", "WAREHOUSE");
        assertThat(response.deptScoped()).isTrue();
        assertThat(response.selfScoped()).isTrue();
        assertThat(response.warehouseIds()).containsExactly(4501L);
        verify(principalCache).evictAll();
    }

    @Test
    void assignAllScopeClearsOtherFlags() {
        when(auditMetadataFactory.current()).thenReturn(audit());
        when(roleMapper.selectById(3002L)).thenReturn(activeRole(3002L));
        when(roleDataScopeMapper.delete(any(LambdaQueryWrapper.class))).thenReturn(1);
        when(roleDataScopeMapper.insert(any(RoleDataScopeEntity.class))).thenReturn(1);

        RoleDataScopeResponse response = service.assign(3002L, new RoleDataScopeAssignRequest(
                true, true, true, true, List.of(4501L)
        ));

        ArgumentCaptor<RoleDataScopeEntity> captor = ArgumentCaptor.forClass(RoleDataScopeEntity.class);
        verify(roleDataScopeMapper).insert(captor.capture());
        assertThat(captor.getValue().getScopeType()).isEqualTo("ALL");
        assertThat(response.hasAllScope()).isTrue();
        assertThat(response.warehouseIds()).isEmpty();
    }

    @Test
    void getAssignedAggregatesExistingRows() {
        when(auditMetadataFactory.current()).thenReturn(audit());
        when(roleMapper.selectById(3002L)).thenReturn(activeRole(3002L));
        when(roleDataScopeMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                scopeRow(3002L, "POST", null),
                scopeRow(3002L, "WAREHOUSE", 4502L),
                scopeRow(3002L, "WAREHOUSE", 4501L)
        ));

        RoleDataScopeResponse response = service.getAssigned(3002L);

        assertThat(response.roleId()).isEqualTo(3002L);
        assertThat(response.postScoped()).isTrue();
        assertThat(response.deptScoped()).isFalse();
        assertThat(response.warehouseIds()).containsExactly(4501L, 4502L);
    }

    @Test
    void assignRejectsMissingRole() {
        when(auditMetadataFactory.current()).thenReturn(audit());
        when(roleMapper.selectById(3002L)).thenReturn(null);

        assertThatThrownBy(() -> service.assign(3002L, new RoleDataScopeAssignRequest(
                false, true, false, false, List.of()
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("角色不存在");
    }

    private static RoleDataScopeEntity scopeRow(Long roleId, String scopeType, Long warehouseId) {
        RoleDataScopeEntity entity = new RoleDataScopeEntity();
        entity.setRoleId(roleId);
        entity.setScopeType(scopeType);
        entity.setWarehouseId(warehouseId);
        return entity;
    }

    private static AuditMetadata audit() {
        return new AuditMetadata(4001L, 1L, 1L, LocalDateTime.of(2026, 7, 17, 12, 0));
    }

    private static RoleEntity activeRole(Long id) {
        RoleEntity role = new RoleEntity();
        role.setId(id);
        role.setCompanyId(1L);
        role.setAccountBookId(1L);
        role.setDeletedFlag(0);
        role.setStatus("ACTIVE");
        return role;
    }

    private static WarehouseEntity activeWarehouse(Long id) {
        WarehouseEntity warehouse = new WarehouseEntity();
        warehouse.setId(id);
        warehouse.setCompanyId(1L);
        warehouse.setAccountBookId(1L);
        warehouse.setDeletedFlag(0);
        warehouse.setStatus("ACTIVE");
        return warehouse;
    }
}
